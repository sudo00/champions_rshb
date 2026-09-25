import base64
from io import BytesIO
from unittest.mock import patch

from fastapi.testclient import TestClient
from PIL import Image
import pytest

from api.main import app
from api.catalog import catalog_data
from api.scans import ScanJob, to_status_response

client = TestClient(app)
SLUG = 'agora-yachting-cabernet-sauvignon'


def image_bytes():
    data = BytesIO()
    Image.new('RGB', (20, 30), 'white').save(data, 'PNG')
    return data.getvalue()


def result(slugs=None):
    slugs = slugs or list(catalog_data()[0])[:5]
    return {'slug':slugs[0], 'candidates':[{'slug':s,'score':.7,'rank':i+1} for i,s in enumerate(slugs)],
            'catalogSha256':catalog_data()[1], 'recognitionStatus':'candidates_unverified',
            'observations':[{'text':'visible text','polygon_original':[[1,2],[3,4]]}],
            'observedFields':{'vintages':[]}, 'regions':[], 'scoreIsProbability':False}


def test_real_catalogue_and_nullable_unknown_metadata():
    response=client.get('/v1/wines/search',params={'per_page':5}).json()
    assert response['totalCount']==2103
    assert len(response['wines'])==5
    card=client.get('/v1/wines/'+SLUG).json()['wine']
    assert card['id']==card['slug']==SLUG
    assert card['rating'] is None and card['price'] is None and card['vintage'] is None
    assert client.get('/v1/wines/not-a-real-slug').json()['wine'] is None


def test_scan_confirmation_route_keeps_model_result_and_returns_user_choice():
    import uuid
    scan_id = str(uuid.uuid4())
    data = result()
    data['userConfirmation'] = {'slug': data['candidates'][1]['slug'], 'source': 'user'}
    with patch('api.main.confirm_job', return_value=ScanJob(scan_id, 'done', True, result=data)):
        response = client.post(f'/v1/wines/scan/{scan_id}/confirmation', json={'slug': data['userConfirmation']['slug']})
    assert response.status_code == 200
    assert response.json()['userConfirmation'] == data['userConfirmation']
    assert response.json()['slug'] == data['slug']
    with patch('api.main.confirm_job', side_effect=ValueError('not a candidate')):
        assert client.post(f'/v1/wines/scan/{scan_id}/confirmation', json={'slug': 'wrong'}).status_code == 409


def test_health_and_readiness_are_distinct():
    assert client.get('/health').status_code==200
    with patch('api.main.recognition_ready',return_value=False):
        assert client.get('/ready').status_code==503
    with patch('api.main.recognition_ready',return_value=True):
        assert client.get('/ready').json()['catalogCount']==2103


def test_scan_preserves_image_and_alternatives_flag():
    image=image_bytes()
    job=ScanJob('id','pending',False,'key')
    with patch('api.main.upload_scan_image',return_value='key') as upload, patch('api.main.create_pending',return_value=job), patch('api.main.publish_scan') as publish:
        response=client.post('/v1/wines/scan',json={'imageBase64':base64.b64encode(image).decode(),'includeAlternatives':False})
    assert response.json()['scanId']=='id'
    assert upload.call_args.args[1]==image
    assert publish.call_args.args[0]['includeAlternatives'] is False
    assert publish.call_args.args[0]['applyCatalogRefusal'] is True


@pytest.mark.parametrize('data',['','garbage',base64.b64encode(b'not image').decode()])
def test_bad_images_never_enqueue(data):
    with patch('api.main.publish_scan') as publish:
        body=client.post('/v1/wines/scan',json={'imageBase64':data}).json()
    assert body['success'] is False
    publish.assert_not_called()


def test_storage_failure_is_not_sent_to_worker_as_empty_image():
    with patch('api.main.upload_scan_image',side_effect=RuntimeError('down')), patch('api.main.create_pending') as pending:
        response=client.post('/v1/wines/scan',json={'imageBase64':base64.b64encode(image_bytes()).decode()})
    assert response.status_code==503
    pending.assert_not_called()


def test_status_has_top5_and_shared_image_observations():
    body=to_status_response(ScanJob('id','done',True,result=result())).model_dump()
    assert len(body['candidates'])==5 and len(body['alternatives'])==4
    assert body['wine']['slug']==body['candidates'][0]['slug']==body['slug']
    assert body['observations']==result()['observations']
    assert body['confidence'] is None and body['scoreIsProbability'] is False
    assert 'f1_top1' not in body and 'f1_top5' not in body
    single=to_status_response(ScanJob('id','done',False,result=result())).model_dump()
    assert len(single['candidates'])==1 and single['alternatives']==[]
    assert single['slug']==body['slug']


def test_catalogue_mismatch_does_not_return_wrong_card():
    data=result();data['catalogSha256']='other'
    with pytest.raises(RuntimeError,match='catalogues differ'):
        to_status_response(ScanJob('id','done',True,result=data))


def test_audited_metadata_only_revision_keeps_old_scan_readable():
    data=result();data['catalogSha256']='383f3e6bb73c8892dec0a58b39ca7f4b8ed026f4433a865b69cce65a6ab56576'
    body=to_status_response(ScanJob('id','done',True,result=data))
    assert body.wine.slug==data['slug'] and body.catalogSha256==data['catalogSha256']


def test_no_target_is_unknown_without_fabricated_card():
    data=result();data.update(slug='unknown',candidates=[],recognitionStatus='no_target')
    body=to_status_response(ScanJob('id','done',True,result=data))
    assert body.slug=='unknown' and body.wine is None and not body.candidates


def test_catalogue_refusal_preserves_evidence_without_proposing_wrong_card():
    data=result();data.update(slug='unknown',candidates=[],recognitionStatus='not_in_catalog',
                              catalogRefusal={'reject':True,'score_is_probability':False},
                              message='Вино не найдено в каталоге.')
    for flag in (True,False):
        body=to_status_response(ScanJob('id','done',flag,result=data))
        assert body.slug=='unknown' and body.wine is None and body.candidates==[] and body.alternatives==[]
        assert body.recognitionStatus=='not_in_catalog' and body.catalogRefusal['reject']
        assert body.observations==data['observations'] and body.confidence is None


def test_match_scores_survive_projection_including_refusal_and_output_limit():
    data=result()
    for i,candidate in enumerate(data['candidates']):
        candidate.update(matchScore=.09-i*.01,matchScoreIsProbability=False)
    diagnostic={'status':'scored','scoreIsProbability':False,'candidates':[
        {k:c[k] for k in ('slug','matchScore','rank')} for c in data['candidates']]}
    data['candidateScoring']=diagnostic
    for flag in (True,False):
        body=to_status_response(ScanJob('id','done',flag,result=data))
        assert body.candidates[0]['matchScore']==.09 and body.candidates[0]['score']==.7
        assert body.candidateScoring==diagnostic and body.confidence is None
    data.update(slug='unknown',candidates=[],recognitionStatus='not_in_catalog')
    body=to_status_response(ScanJob('id','done',True,result=data))
    assert body.wine is None and body.candidates==[] and body.candidateScoring==diagnostic


def test_eval_returns_flat_unknown_when_no_target_was_detected():
    from api.contracts import ScanAcceptedResponse
    accepted=ScanAcceptedResponse(success=True,scanId='id',status='pending')
    data=result();data.update(slug='unknown',candidates=[],recognitionStatus='no_target')
    with patch('api.main.recognition_ready',return_value=True), patch('api.main.submit_scan',return_value=accepted), patch('api.main.get_job',return_value=ScanJob('id','done',False,result=data)):
        response=client.post('/v1/eval/predict',files={'image':('example.webp',image_bytes(),'image/webp')})
    assert response.status_code==200 and response.json()=={'slug':'unknown'}


def test_eval_exact_multipart_contract():
    from api.contracts import ScanAcceptedResponse
    accepted=ScanAcceptedResponse(success=True,scanId='id',status='pending')
    with patch('api.main.recognition_ready',return_value=True), patch('api.main.submit_scan',return_value=accepted) as submit, patch('api.main.get_job',return_value=ScanJob('id','done',False,result=result([SLUG]))):
        response=client.post('/v1/eval/predict',files={'image':('example.webp',image_bytes(),'image/webp')})
    assert response.status_code==200 and response.json()=={'slug':SLUG}
    assert submit.call_args.args==(image_bytes(),False)
    assert submit.call_args.kwargs=={'apply_catalog_refusal':False,'use_reviewed_sweetness':True}


def test_eval_refusal_bypass_is_forwarded_to_queue():
    job=ScanJob('id','pending',False,'key')
    with patch('api.main.upload_scan_image',return_value='key'), patch('api.main.create_pending',return_value=job), patch('api.main.publish_scan') as publish:
        from api.main import submit_scan
        submit_scan(image_bytes(),False,apply_catalog_refusal=False)
    assert publish.call_args.args[0]['applyCatalogRefusal'] is False
    assert publish.call_args.args[0]['useReviewedSweetness'] is True


def test_eval_cold_worker_fails_before_enqueue():
    with patch('api.main.recognition_ready',return_value=False), patch('api.main.submit_scan') as submit:
        response=client.post('/v1/eval/predict',files={'image':('photo.png',image_bytes())})
    assert response.status_code==503
    submit.assert_not_called()


def test_eval_timeout_is_an_http_error_not_unknown():
    from api.contracts import ScanAcceptedResponse
    with patch.dict('os.environ',{'EVAL_WAIT_SECONDS':'0'}), patch('api.main.recognition_ready',return_value=True), patch('api.main.submit_scan',return_value=ScanAcceptedResponse(success=True,scanId='id',status='pending')):
        response=client.post('/v1/eval/predict',files={'image':('photo.png',image_bytes())})
    assert response.status_code==504 and 'slug' not in response.json()


def test_openapi_documents_both_scan_contracts():
    spec=client.get('/openapi.json').json()
    assert 'multipart/form-data' in spec['paths']['/v1/eval/predict']['post']['requestBody']['content']
    assert '/v1/wines/scan/{scan_id}' in spec['paths']


def test_reviewed_sweetness_is_projected_into_catalogue_and_old_database_rows():
    from api.catalog import wine_from_file, wine_from_row
    base = 'zhemchuzhnaya-9-pino-nuar-muskat-rozovyj'
    for suffix, sugar in [('', 'Сухое'), ('-1', 'Полусухое'), ('-2', 'Полусладкое')]:
        slug = base + suffix
        assert wine_from_file(slug).sweetness == sugar
        old_payload = {'id': slug, 'name': 'Old name', 'imageUrl': '/old.jpg'}
        card = wine_from_row((slug, 'Old name', old_payload, None))
        assert card.sweetness == sugar
        assert card.imageUrl == '/old.jpg'
        assert 'sweetness' not in old_payload


def test_sweetness_diagnostic_is_preserved_in_scan_response():
    raw = result()
    raw['sweetnessRanking'] = {'applied': True, 'reason': 'reviewed_sugar_matches_label'}
    response = to_status_response(ScanJob('id', 'done', True, 'key', raw))
    assert response.sweetnessRanking == raw['sweetnessRanking']
