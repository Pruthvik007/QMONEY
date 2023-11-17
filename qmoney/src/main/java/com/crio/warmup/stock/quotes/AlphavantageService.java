
package com.crio.warmup.stock.quotes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.crio.warmup.stock.dto.AlphavantageCandle;
import com.crio.warmup.stock.dto.AlphavantageDailyResponse;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.web.client.RestTemplate;


public class AlphavantageService implements StockQuotesService {

  private RestTemplate restTemplate;

  public AlphavantageService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws JsonProcessingException, StockQuoteServiceException {
    String url = buildUri(symbol);
    String str = restTemplate.getForObject(url, String.class);
    List<Candle> res = new ArrayList<>();
    try {
      Map<LocalDate, AlphavantageCandle> m =
          getObjectMapper().readValue(str, AlphavantageDailyResponse.class).getCandles();
      for (LocalDate i : m.keySet()) {
        if (i.isBefore(to.plusDays(1)) && i.isAfter(from.minusDays(1))) {
          AlphavantageCandle alphavantageCandle = m.get(i);
          alphavantageCandle.setDate(i);
          res.add(alphavantageCandle);
        }
      }
    } catch (RuntimeException re) {
      throw new StockQuoteServiceException(str);
    }
    Collections.sort(res, getComparator());
    return res;

  }

  private Comparator<Candle> getComparator() {
    return Comparator.comparing(Candle::getDate);
  }

  protected String buildUri(String symbol) {
    return "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol=" + symbol
        + "&outputsize=full&apikey=" + "M8455LQB3EXJA0YR";

  }

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }


  private List<Candle> getStocksBetween(Map<LocalDate, AlphavantageCandle> candles, LocalDate from,
      LocalDate to) {
    List<Candle> list = candles.entrySet().stream()
        .filter(entry -> !entry.getKey().isBefore(from) && !entry.getKey().isAfter(to))
        .map(entry -> {
          entry.getValue().setDate(entry.getKey());
          return entry;
        }).map(entry -> entry.getValue()).collect(Collectors.toList());
    Collections.reverse(list);
    return list;
  }
}

