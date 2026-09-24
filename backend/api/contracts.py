from pydantic import BaseModel, Field


class CountryDto(BaseModel):
    name: str = Field(description="Название страны", examples=["Россия"])
    code: str = Field(description="ISO-код страны", examples=["RU"])


class RegionDto(BaseModel):
    name: str = Field(description="Регион / винодельческая зона", examples=["Кубань"])
    country: CountryDto | None = Field(default=None, description="Страна региона")


class VarietyDto(BaseModel):
    name: str = Field(description="Сорт винограда", examples=["Каберне Совиньон"])


class StyleDto(BaseModel):
    name: str = Field(description="Стиль вина", examples=["Dry Red"])


class WineryDto(BaseModel):
    name: str = Field(description="Производитель", examples=["Фанагория"])


class WineDto(BaseModel):
    id: str = Field(description="Идентификатор позиции каталога", examples=["fanagoria-cabernet"])
    slug: str
    name: str = Field(description="Название вина")
    vintage: int | None = Field(default=None, description="Год урожая")
    rating: float | None = Field(default=None, description="Рейтинг, если есть в источнике")
    reviewsCount: int | None = None
    category: str | None = None
    grapes: str | None = None
    colorShade: str | None = None
    price: float | None = Field(default=None, description="Цена")
    currency: str | None = Field(default=None, description="Валюта", examples=["RUB"])
    region: RegionDto | None = None
    country: CountryDto | None = None
    variety: VarietyDto | None = None
    style: StyleDto | None = None
    alcoholPercentage: float | None = Field(default=None, description="Алкоголь, %")
    imageUrl: str | None = Field(default=None, description="URL этикетки / бутылки")
    description: str | None = None
    foodPairing: list[str] = Field(default_factory=list, description="Гастрономические сочетания")
    winery: WineryDto | None = None


class ScanRequest(BaseModel):
    imageBase64: str = Field(description="Фото этикетки в Base64, допускается data-URL")
    includeAlternatives: bool = Field(
        default=True,
        description="Вернуть альтернативные совпадения (аналог топ-5)",
    )


class ScanAcceptedResponse(BaseModel):
    success: bool = Field(description="Задача принята в очередь")
    scanId: str | None = Field(default=None, description="Идентификатор для polling")
    status: str = Field(description="pending сразу после постановки")
    error: str | None = Field(default=None, description="Текст ошибки, если success=false")


class ScanStatusResponse(BaseModel):
    success: bool
    scanId: str | None = Field(default=None, description="Идентификатор сканирования")
    status: str = Field(description="pending | processing | done | failed")
    wine: WineDto | None = Field(default=None, description="Лучшее совпадение, когда status=done")
    confidence: float | None = Field(default=None, description="Пока не откалибрована; не заменять score")
    alternatives: list[WineDto] = Field(default_factory=list, description="Другие кандидаты")
    slug: str | None = None
    candidates: list[dict] = Field(default_factory=list, description="До пяти кандидатов; первый совпадает с wine")
    recognitionStatus: str | None = None
    catalogRefusal: dict = Field(default_factory=dict, description="Диагностика осторожного отказа; score не является вероятностью")
    candidateScoring: dict = Field(default_factory=dict, description="Абсолютные matchScore 0–1 для исходного Top-5; не вероятности и не дополнительный фильтр отказа")
    recommendations: list[dict] = Field(default_factory=list, description="До пяти аналогов при not_in_catalog; отдельны от совпадений, с wine, textSimilarity и причинами подбора")
    recommendationContext: dict = Field(default_factory=dict, description="Прочитанные признаки, их источники и статус подбора аналогов")
    message: str | None = None
    cylinder: dict = Field(default_factory=dict)
    scoreIsProbability: bool = False
    observations: list[dict] = Field(default_factory=list)
    observedFields: dict = Field(default_factory=dict)
    regions: list[dict] = Field(default_factory=list)
    imageSize: list[int] = Field(default_factory=list)
    coordinateSystem: str | None = None
    target: dict = Field(default_factory=dict)
    warnings: list[str] = Field(default_factory=list)
    version: str | None = None
    catalogSha256: str | None = None
    timingsSeconds: dict = Field(default_factory=dict)
    error: str | None = None


class SearchResponse(BaseModel):
    wines: list[WineDto] = Field(default_factory=list)
    totalCount: int = Field(description="Всего найденных позиций")
    page: int = Field(description="Текущая страница, с 1")
    hasMore: bool = Field(description="Есть ли следующая страница")


class WineDetailResponse(BaseModel):
    wine: WineDto | None = Field(default=None, description="Карточка; null если id неизвестен")


class SommelierChatMessageDto(BaseModel):
    role: str = Field(description="Роль: user или assistant", examples=["user"])
    content: str = Field(description="Текст сообщения")


class SommelierWineContextDto(BaseModel):
    wineId: str
    wineName: str
    region: str | None = None
    variety: str | None = None
    vintage: int | None = None
    rating: float | None = None
    style: str | None = None


class SommelierChatRequest(BaseModel):
    messages: list[SommelierChatMessageDto] = Field(description="История диалога")
    wineContext: SommelierWineContextDto | None = Field(
        default=None,
        description="Вино, с которым связан вопрос",
    )


class SommelierChatResponse(BaseModel):
    success: bool
    message: SommelierChatMessageDto | None = Field(
        default=None,
        description="Ответ ассистента",
    )
    error: str | None = None
