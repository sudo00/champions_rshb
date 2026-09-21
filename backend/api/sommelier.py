from api.contracts import SommelierChatMessageDto, SommelierWineContextDto


def reply(messages: list[SommelierChatMessageDto], wine_context: SommelierWineContextDto | None) -> str:
    question = next((item.content for item in reversed(messages) if item.role == "user"), "")
    wine_name = wine_context.wineName if wine_context else "это вино"
    region = wine_context.region if wine_context else None
    variety = wine_context.variety if wine_context else None
    style = wine_context.style if wine_context else None

    lowered = question.lower()
    if "подавать" in lowered or "сочетан" in lowered:
        if style and "red" in style.lower():
            pairings = "мясными блюдами, сырами с плесенью, тёмным шоколадом."
        elif style and "white" in style.lower():
            pairings = "морепродуктами, птицей, салатами."
        elif style and "sparkling" in style.lower():
            pairings = "закусками, устрицами, лёгкими салатами."
        else:
            pairings = "сырами, орехами и основными блюдами по сезону."
        return f"{wine_name} сочетается с {pairings}"
    if "аналог" in lowered or "дешев" in lowered:
        return (
            f"Для {wine_name} из {region or 'известного региона'} "
            f"({variety or 'сорт'}) ищите вина того же региона и сорта."
        )
    if "регион" in lowered:
        if region:
            return f"{region} задаёт характер {wine_name}. Это типичный пример местного стиля."
        return f"{wine_name}: уточните, что именно интересует в регионе."
    if "выдерж" in lowered or "созрев" in lowered:
        years = "3-5 лет" if style and "red" in style.lower() else "1-3 года"
        return f"{wine_name} обычно пьют через {years} после урожая."
    if "температ" in lowered:
        if style and "red" in style.lower():
            temp = "16-18°C"
        elif style and "white" in style.lower():
            temp = "8-12°C"
        elif style and "sparkling" in style.lower():
            temp = "6-8°C"
        else:
            temp = "10-14°C"
        return f"Подавайте {wine_name} при {temp}."
    extra = ""
    if region:
        extra += f" Регион: {region}."
    if variety:
        extra += f" Сорт: {variety}."
    return f"{wine_name} — хороший выбор.{extra} Задайте вопрос про гастропару, регион или аналоги."
