from api.contracts import CountryDto, RegionDto, StyleDto, VarietyDto, WineDto, WineryDto


def _wine(
    *,
    wine_id: str,
    name: str,
    vintage: int | None,
    rating: float,
    reviews_count: int,
    price: float | None,
    currency: str | None,
    region: str,
    country: str,
    country_code: str,
    variety: str,
    style: str,
    alcohol: float,
    image_url: str,
    description: str,
    food_pairing: list[str],
    winery: str,
) -> WineDto:
    country_dto = CountryDto(name=country, code=country_code)
    return WineDto(
        id=wine_id,
        name=name,
        vintage=vintage,
        rating=rating,
        reviewsCount=reviews_count,
        price=price,
        currency=currency,
        region=RegionDto(name=region, country=country_dto),
        country=country_dto,
        variety=VarietyDto(name=variety),
        style=StyleDto(name=style),
        alcoholPercentage=alcohol,
        imageUrl=image_url,
        description=description,
        foodPairing=food_pairing,
        winery=WineryDto(name=winery),
    )


SEED_WINES: list[WineDto] = [
    _wine(
        wine_id="fanagoria-cabernet",
        name="Фанагория Каберне",
        vintage=2021,
        rating=4.2,
        reviews_count=312,
        price=890.0,
        currency="RUB",
        region="Кубань",
        country="Россия",
        country_code="RU",
        variety="Каберне Совиньон",
        style="Dry Red",
        alcohol=13.5,
        image_url="https://images.unsplash.com/photo-1510812431401-41d2bd2722f3?w=400",
        description="Сухое красное с нотами чёрной смородины и специй.",
        food_pairing=["Стейк", "Сыр", "Шашлык"],
        winery="Фанагория",
    ),
    _wine(
        wine_id="abrau-durso-brut",
        name="Абрау-Дюрсо Brut",
        vintage=2020,
        rating=4.4,
        reviews_count=891,
        price=1290.0,
        currency="RUB",
        region="Краснодарский край",
        country="Россия",
        country_code="RU",
        variety="Шардоне / Пино Нуар",
        style="Sparkling",
        alcohol=12.0,
        image_url="https://images.unsplash.com/photo-1592841200221-a6898f307baa?w=400",
        description="Игристое с цитрусом и свежей кислотностью.",
        food_pairing=["Устрицы", "Закуски", "Суши"],
        winery="Абрау-Дюрсо",
    ),
    _wine(
        wine_id="massaandra-muscat",
        name="Массандра Мускат белый",
        vintage=2019,
        rating=4.1,
        reviews_count=156,
        price=740.0,
        currency="RUB",
        region="Крым",
        country="Россия",
        country_code="RU",
        variety="Мускат",
        style="White",
        alcohol=12.5,
        image_url="https://images.unsplash.com/photo-1474722883778-792e7990302f?w=400",
        description="Ароматный белый мускат с тонами цветов и мёда.",
        food_pairing=["Десерты", "Фрукты", "Сыр"],
        winery="Массандра",
    ),
    _wine(
        wine_id="lefkadia-sauvignon",
        name="Лефкадия Совиньон Блан",
        vintage=2022,
        rating=4.0,
        reviews_count=204,
        price=1100.0,
        currency="RUB",
        region="Кубань",
        country="Россия",
        country_code="RU",
        variety="Совиньон Блан",
        style="Dry White",
        alcohol=12.5,
        image_url="https://images.unsplash.com/photo-1566995541428-f2246c17cda1?w=400",
        description="Свежий белый с нотами крыжовника и лайма.",
        food_pairing=["Морепродукты", "Салаты", "Птица"],
        winery="Лефкадия",
    ),
]


def search_wines(query: str, page: int, per_page: int) -> tuple[list[WineDto], int, bool]:
    q = query.strip().lower()
    matched = [
        wine
        for wine in SEED_WINES
        if not q
        or q in wine.name.lower()
        or (wine.region and q in wine.region.name.lower())
        or (wine.variety and q in wine.variety.name.lower())
        or (wine.winery and q in wine.winery.name.lower())
    ]
    page = max(page, 1)
    per_page = max(per_page, 1)
    start = (page - 1) * per_page
    chunk = matched[start : start + per_page]
    return chunk, len(matched), start + len(chunk) < len(matched)


def get_wine(wine_id: str) -> WineDto | None:
    return next((wine for wine in SEED_WINES if wine.id == wine_id), None)


def stub_scan(include_alternatives: bool) -> tuple[WineDto, list[WineDto]]:
    main = SEED_WINES[0]
    alternatives = SEED_WINES[1:4] if include_alternatives else []
    return main, alternatives
