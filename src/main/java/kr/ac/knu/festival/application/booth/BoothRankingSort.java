package kr.ac.knu.festival.application.booth;

public enum BoothRankingSort {
    LIKES("likes"),
    WAITING_ASC("waiting-asc"),
    NAME_ASC("name-asc");

    private final String value;

    BoothRankingSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static BoothRankingSort from(String value) {
        if (value == null || value.isBlank()) {
            return LIKES;
        }
        for (BoothRankingSort sort : values()) {
            if (sort.value.equalsIgnoreCase(value)) {
                return sort;
            }
        }
        return LIKES;
    }
}
