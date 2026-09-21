"""Regression cases for metadata audit false positives and real disagreements."""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from audit_catalog_contradictions import check_row, mentioned_grapes, sugar_terms


def card(**updates: str) -> dict[str, str]:
    row = {
        "Название вина": "Кюве", "Категория": "Белое", "Цвет": "Золотистый",
        "Сорт винограда": "Шардоне", "Описание": "", "Slug": "cuvee",
    }
    row.update(updates)
    return row


def rules(row: dict[str, str]) -> set[str]:
    return {finding["rule"] for finding in check_row(row)}


class ContradictionTests(unittest.TestCase):
    def test_white_grape_name_is_not_wine_color(self):
        self.assertNotIn("title_category", rules(card(**{
            "Название вина": "Реликта Антей Магарачский, Мускат Белый", "Категория": "Розовое",
        })))

    def test_explicit_product_color(self):
        self.assertIn("title_category", rules(card(**{
            "Название вина": "Полусладкое розовое", "Категория": "Красное",
        })))

    def test_orange_is_not_red_and_grape_color_is_not_inferred(self):
        self.assertIn("title_category", rules(card(**{
            "Название вина": "Оранжевое из Белого", "Категория": "Красное",
        })))
        self.assertFalse(rules(card(**{"Название вина": "Пино Нуар", "Сорт винограда": "Пино Нуар"})))

    def test_shade_conflict_and_amber_exception(self):
        self.assertIn("shade_category", rules(card(**{"Категория": "Красное", "Цвет": "Светло-соломенный"})))
        self.assertNotIn("shade_category", rules(card(**{"Категория": "Оранжевое", "Цвет": "Янтарный"})))

    def test_red_fruit_description_is_not_wine_color(self):
        self.assertNotIn("description_category", rules(card(**{"Описание": "Аромат красных ягод и белых цветов"})))
        self.assertIn("description_category", rules(card(**{"Описание": "Это красное вино обладает свежим вкусом."})))

    def test_confusable_letters_and_partial_blend(self):
        self.assertEqual(mentioned_grapes("Сabernet Sauvignon - Cabernet Franc"), {"Каберне Совиньон", "Каберне Фран"})
        self.assertNotIn("title_grapes", rules(card(**{
            "Название вина": "Сabernet Sauvignon - Cabernet Franc", "Сорт винограда": "Каберне Совиньон",
        })))

    def test_grape_disagreement(self):
        self.assertIn("title_grapes", rules(card(**{"Название вина": "Мерло", "Сорт винограда": "Совиньон Блан"})))

    def test_demisecond_name_repeated_in_slug_does_not_hide_extra_brut(self):
        self.assertEqual(sugar_terms("Demi-Sec"), {"полусладкое"})
        self.assertIn("title_slug_sugar", rules(card(**{
            "Название вина": "Demi-Sec Muscat Nectar", "Slug": "esse-demi-sec-muscat-nectar-beloe-ekstra-bryut-115",
        })))

    def test_year_in_brand_does_not_establish_vintage(self):
        self.assertNotIn("title_slug_year", rules(card(**{"Название вина": "Потенциал 2026, 2014", "Slug": "potenczial-2026"})))
        self.assertIn("title_slug_year", rules(card(**{"Название вина": "Кюве, 2024", "Slug": "cuvee-2025"})))

    def test_broad_brut_wording_is_not_a_sugar_conflict(self):
        self.assertNotIn("title_slug_sugar", rules(card(**{
            "Название вина": "Extra Brut", "Slug": "extra-brut-beloe-bryut-12",
        })))
        self.assertNotIn("title_slug_sugar", rules(card(**{
            "Название вина": "Брют", "Slug": "bryut-beloe-suhoe-12",
        })))


if __name__ == "__main__":
    unittest.main()
