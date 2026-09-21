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


def test_no_target_is_unknown_without_fabricated_card():
    data=result();data.update(slug='unknown',candidates=[],recognitionStatus='no_target')
    body=to_status_response(ScanJob('id','done',True,result=data))
    assert body.slug=='unknown' and body.wine is None and not body.candidates


def test_eval_exact_multipart_contract():
    from api.contracts import ScanAcceptedResponse
    accepted=ScanAcceptedResponse(success=True,scanId='id',status='pending')
    with patch('api.main.recognition_ready',return_value=True), patch('api.main.submit_scan',return_value=accepted) as submit, patch('api.main.get_job',return_value=ScanJob('id','done',False,result=result([SLUG]))):
        response=client.post('/v1/eval/predict',files={'image':('example.webp',image_bytes(),'image/webp')})
    assert response.status_code==200 and response.json()=={'slug':SLUG}
    assert submit.call_args.args==(image_bytes(),False)


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
