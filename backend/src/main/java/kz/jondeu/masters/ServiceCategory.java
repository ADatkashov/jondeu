package kz.jondeu.masters;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public enum ServiceCategory {
    APARTMENT_RENOVATION("Ремонт квартиры", "Пәтер жөндеу", "Apartment renovation"),
    HOUSE_RENOVATION("Ремонт дома", "Үй жөндеу", "House renovation"),
    BATHROOM("Ремонт ванной", "Ванна бөлмесін жөндеу", "Bathroom renovation"),
    KITCHEN("Ремонт кухни", "Асүйді жөндеу", "Kitchen renovation"),
    ELECTRICAL("Электромонтаж", "Электр монтажы", "Electrical installation"),
    PLUMBING("Сантехника", "Сантехника", "Plumbing"),
    PAINTING("Покраска", "Бояу жұмыстары", "Painting"),
    FLOORING("Полы", "Едендер", "Flooring"),
    TILING("Плитка", "Плитка", "Tiling"),
    PLASTERING("Штукатурка", "Сылақ", "Plastering"),
    DRYWALL("Гипсокартон", "Гипсокартон", "Drywall"),
    DOORS_WINDOWS("Двери и окна", "Есіктер мен терезелер", "Doors and windows"),
    FURNITURE("Сборка мебели", "Жиһаз құрастыру", "Furniture installation"),
    HEATING_VENTILATION("Отопление и вентиляция", "Жылу және желдету", "Heating and ventilation"),
    GENERAL_CONSTRUCTION("Общестроительные работы", "Жалпы құрылыс", "General construction"),
    CLEANING("Уборка после ремонта", "Жөндеуден кейінгі тазалау", "Cleaning after renovation");

    public final String ru, kk, en;

    ServiceCategory(String ru, String kk, String en) { this.ru = ru; this.kk = kk; this.en = en; }

    public static boolean valid(String code) { return Arrays.stream(values()).anyMatch(c -> c.name().equals(code)); }

    public static List<Map<String, String>> all() {
        return Arrays.stream(values()).map(c -> Map.of("code", c.name(), "ru", c.ru, "kk", c.kk, "en", c.en)).toList();
    }
}
