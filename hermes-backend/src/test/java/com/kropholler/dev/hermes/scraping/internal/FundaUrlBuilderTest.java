package com.kropholler.dev.hermes.scraping.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundaUrlBuilderTest {

    private final FundaUrlBuilder builder = new FundaUrlBuilder();

    @Test
    void build_withCityOnly_returnsBaseUrl() {
        String url = builder.build("amsterdam", null, null, null, null, 1);
        assertThat(url).isEqualTo(
            "https://www.funda.nl/zoeken/koop?selected_area=%5B%22amsterdam%22%5D&search_result=1"
        );
    }

    @Test
    void build_withAllFilters_includesAllParams() {
        String url = builder.build("rotterdam", 200000, 500000, 60, 150, 1);
        assertThat(url).contains("price_min=200000");
        assertThat(url).contains("price_max=500000");
        assertThat(url).contains("floor_area_min=60");
        assertThat(url).contains("floor_area_max=150");
    }

    @Test
    void build_withPageTwo_includesPageParam() {
        String url = builder.build("amsterdam", null, null, null, null, 2);
        assertThat(url).contains("search_result=2");
    }

    @Test
    void build_pageLimitAboveFive_throwsException() {
        assertThatThrownBy(() -> builder.build("amsterdam", null, null, null, null, 6))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("page");
    }
}
