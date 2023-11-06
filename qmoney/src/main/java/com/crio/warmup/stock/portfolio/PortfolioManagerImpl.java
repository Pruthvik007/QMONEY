
package com.crio.warmup.stock.portfolio;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.SECONDS;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {

  RestTemplate restTemplate;

  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  public PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  // TODO: CRIO_TASK_MODULE_REFACTOR
  // 1. Now we want to convert our code into a module, so we will not call it from main anymore.
  // Copy your code from Module#3 PortfolioManagerApplication#calculateAnnualizedReturn
  // into #calculateAnnualizedReturn function here and ensure it follows the method signature.
  // 2. Logic to read Json file and convert them into Objects will not be required further as our
  // clients will take care of it, going forward.

  // Note:
  // Make sure to exercise the tests inside PortfolioManagerTest using command below:
  // ./gradlew test --tests PortfolioManagerTest

  // CHECKSTYLE:OFF

  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  // CHECKSTYLE:OFF

  // TODO: CRIO_TASK_MODULE_REFACTOR
  // Extract the logic to call Tiingo third-party APIs to a separate function.
  // Remember to fill out the buildUri function and use that.

  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws JsonProcessingException {
        String Url = buildUri(symbol, from, to);
        TiingoCandle[] tc = this.restTemplate.getForObject(Url, TiingoCandle[].class);
        return Arrays.asList(tc);
  }

  protected static String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
    // 7a04e1b4e67ef19eb6ef84a7e545bb3b99921d08
    //e3f3ebbee3b2a7c335c5065488edd29356b01c92
    
    String uriTemplate =
        "https://api.tiingo.com/tiingo/daily/" + symbol + "/prices?startDate=" + startDate
            + "&endDate=" + endDate + "&token=" + "7a04e1b4e67ef19eb6ef84a7e545bb3b99921d08";
    return uriTemplate;
  }

  static Double getOpeningPriceOnStartDate(List<Candle> candles) {
    return candles.get(0).getOpen();
  }

  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
    return candles.get(candles.size() - 1).getClose();
  }

  // public List<Candle> fetchCandles(PortfolioTrade trade, LocalDate endDate) {
  //   String Url = buildUri(trade.getSymbol(), trade.getPurchaseDate(), endDate);
  //   TiingoCandle[] tc = this.restTemplate.getForObject(Url, TiingoCandle[].class);
  //   return Arrays.asList(tc);
  // }

  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate, PortfolioTrade trade,
      Double buyPrice, Double sellPrice) {
    Double totalReturns = (sellPrice - buyPrice) / buyPrice;
    Double years = trade.getPurchaseDate().until(endDate, ChronoUnit.DAYS) / 365.24;
    Double annualizedReturns = Math.pow(1 + totalReturns, (1 / years)) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturns, totalReturns);
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,
      LocalDate endDate)  {
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    for (PortfolioTrade trade : portfolioTrades) {
      if (endDate.isBefore(trade.getPurchaseDate())) {
        throw new RuntimeException();
      }
      List<Candle> candles = new ArrayList<>();
      try {
        candles=getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);
      } catch (JsonProcessingException e) {
        // e.printStackTrace();
      }
      if (candles.size() == 0) {
        continue;
      }
      annualizedReturns.add(calculateAnnualizedReturns(endDate, trade,
          getOpeningPriceOnStartDate(candles), getClosingPriceOnEndDate(candles)));
    }
    if (annualizedReturns.size() == 0) {
      return annualizedReturns;
    }
    annualizedReturns.sort(getComparator());
    return annualizedReturns;
  }
}
