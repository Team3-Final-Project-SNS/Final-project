package com.example.team3final.domain.university.enums;


public enum MajorCategory {

    COMPUTER_SCIENCE("컴퓨터공학"),
    ARTIFICIAL_INTELLIGENCE("인공지능학"),
    SOFTWARE_ENGINEERING("소프트웨어학"),
    ELECTRICAL_ELECTRONIC_ENGINEERING("전기전자공학"),
    WEDDING_BEAUTY("웨딩뷰티학"),

    BUSINESS_ADMINISTRATION("경영학"),
    ECONOMICS("경제학"),
    ACCOUNTING("회계학"),
    INTERNATIONAL_TRADE("국제무역학"),
    PUBLIC_ADMINISTRATION("행정학"),

    LAW("법학"),
    POLICE_ADMINISTRATION("경찰행정학"),
    SOCIAL_WELFARE("사회복지학"),
    PSYCHOLOGY("심리학"),
    CHILD_EDUCATION("유아교육학"),

    KOREAN_LANGUAGE_LITERATURE("국어국문학"),
    ENGLISH_LANGUAGE_LITERATURE("영어영문학"),
    JAPANESE_LANGUAGE_LITERATURE("일어일문학"),
    CHINESE_LANGUAGE_LITERATURE("중어중문학"),

    MECHANICAL_ENGINEERING("기계공학"),
    CIVIL_ENGINEERING("토목공학"),
    ARCHITECTURE("건축학"),
    CHEMICAL_ENGINEERING("화학공학"),
    INDUSTRIAL_ENGINEERING("산업공학"),

    DATA_SCIENCE("데이터사이언스학"),
    INFORMATION_SECURITY("정보보안학"),
    GAME_ENGINEERING("게임공학"),
    MEDIA_CONTENTS("미디어콘텐츠학"),

    NURSING("간호학"),
    PHYSICAL_THERAPY("물리치료학"),
    CLINICAL_PATHOLOGY("임상병리학"),
    DENTAL_HYGIENE("치위생학"),
    EMERGENCY_MEDICAL_SERVICE("응급구조학"),

    DESIGN("디자인학"),
    VISUAL_DESIGN("시각디자인학"),
    INDUSTRIAL_DESIGN("산업디자인학"),
    FASHION_DESIGN("패션디자인학"),
    BEAUTY_ART("뷰티아트학"),

    SPORTS_SCIENCE("스포츠과학"),
    FOOD_NUTRITION("식품영양학"),
    HOTEL_TOURISM("호텔관광학"),
    CULINARY_ARTS("외식조리학"),

    MUSIC("음악학"),
    PRACTICAL_MUSIC("실용음악학"),
    THEATER_FILM("연극영화학"),
    FINE_ARTS("미술학");

    private final String description;

    MajorCategory(String description) {
        this.description = description;
    }
}