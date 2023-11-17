
package com.crio.warmup.stock;


import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.dto.TotalReturnsDto;
import com.crio.warmup.stock.log.UncaughtExceptionHandler;
import com.crio.warmup.stock.portfolio.PortfolioManager;
import com.crio.warmup.stock.portfolio.PortfolioManagerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.web.client.RestTemplate;


public class PortfolioManagerApplication {

  public static String getToken() {
    // 7a04e1b4e67ef19eb6ef84a7e545bb3b99921d08
    // e3f3ebbee3b2a7c335c5065488edd29356b01c92
    return "e3f3ebbee3b2a7c335c5065488edd29356b01c92";
  }

  public static List<String> mainReadFile(String[] args) throws IOException, URISyntaxException {
    File file = resolveFileFromResources(args[0]);
    ObjectMapper om = getObjectMapper();
    PortfolioTrade[] trades = om.readValue(file, PortfolioTrade[].class);
    List<String> res = new ArrayList<>();
    for (PortfolioTrade trade : trades) {
      res.add(trade.getSymbol());
    }
    return res;
  }

  private static void printJsonObject(Object object) throws IOException {
    Logger logger = Logger.getLogger(PortfolioManagerApplication.class.getCanonicalName());
    ObjectMapper mapper = new ObjectMapper();
    logger.info(mapper.writeValueAsString(object));
  }

  private static File resolveFileFromResources(String filename) throws URISyntaxException {
    return Paths.get(Thread.currentThread().getContextClassLoader().getResource(filename).toURI())
        .toFile();
  }

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  public static List<String> debugOutputs() {

    String valueOfArgument0 = "trades.json";
    String resultOfResolveFilePathArgs0 =
        "/home/crio-user/workspace/projectworks1225-ME_QMONEY_V2/qmoney/bin/main/trades.json";
    String toStringOfObjectMapper = "com.fasterxml.jackson.databind.ObjectMapper@2f9f7dcf";
    String functionNameFromTestFileInStackTrace = "mainReadFile";
    String lineNumberFromTestFileInStackTrace = "29";

    return Arrays.asList(
        new String[] {valueOfArgument0, resultOfResolveFilePathArgs0, toStringOfObjectMapper,
            functionNameFromTestFileInStackTrace, lineNumberFromTestFileInStackTrace});
  }

  public static List<String> mainReadQuotes(String[] args) throws IOException, URISyntaxException {
    if (args.length == 0) {
      return Collections.emptyList();
    }
    File file = resolveFileFromResources(args[0]);
    ObjectMapper om = getObjectMapper();
    PortfolioTrade[] trades = om.readValue(file, PortfolioTrade[].class);
    RestTemplate restTemplate = new RestTemplate();
    List<TotalReturnsDto> totalReturnsDtos = new ArrayList<>();
    for (PortfolioTrade trade : trades) {
      if (LocalDate.parse(args[1]).isBefore(trade.getPurchaseDate())) {
        throw new RuntimeException();
      }
      String url = prepareUrl(trade, LocalDate.parse(args[1]), getToken());
      TiingoCandle[] candles = restTemplate.getForObject(url, TiingoCandle[].class);
      TotalReturnsDto dto =
          new TotalReturnsDto(trade.getSymbol(), candles[candles.length - 1].getClose());
      totalReturnsDtos.add(dto);
    }
    return totalReturnsDtos.stream().sorted(new Comparator<TotalReturnsDto>() {
      @Override
      public int compare(TotalReturnsDto dtoOne, TotalReturnsDto dtoTwo) {
        return dtoOne.getClosingPrice().compareTo(dtoTwo.getClosingPrice());
      }
    }).map(m -> m.getSymbol()).collect(Collectors.toList());
  }

  public static List<PortfolioTrade> readTradesFromJson(String filename)
      throws IOException, URISyntaxException {
    File file = resolveFileFromResources(filename);
    ObjectMapper om = getObjectMapper();
    PortfolioTrade[] trades = om.readValue(file, PortfolioTrade[].class);
    return Arrays.asList(trades);
  }

  public static String prepareUrl(PortfolioTrade trade, LocalDate endDate, String token) {
    String Url = "https://api.tiingo.com/tiingo/daily/" + trade.getSymbol() + "/prices?startDate="
        + trade.getPurchaseDate().toString() + "&endDate=" + endDate + "&token=" + token;
    return Url;
  }

  static Double getOpeningPriceOnStartDate(List<Candle> candles) {
    return candles.get(0).getOpen();
  }

  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
    return candles.get(candles.size() - 1).getClose();

  }

  public static List<Candle> fetchCandles(PortfolioTrade trade, LocalDate endDate, String token) {
    RestTemplate rt = new RestTemplate();
    String Url = prepareUrl(trade, endDate, token);
    TiingoCandle[] tc = rt.getForObject(Url, TiingoCandle[].class);
    return Arrays.asList(tc);
  }

  public static List<AnnualizedReturn> mainCalculateSingleReturn(String[] args)
      throws IOException, URISyntaxException {
    if (args.length == 0) {
      return Collections.emptyList();
    }
    List<PortfolioTrade> trades = readTradesFromJson(args[0]);
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    for (PortfolioTrade trade : trades) {
      if (LocalDate.parse(args[1]).isBefore(trade.getPurchaseDate())) {
        throw new RuntimeException();
      }
      List<Candle> candles = fetchCandles(trade, LocalDate.parse(args[1]), getToken());
      if (candles.size() == 0) {
        continue;
      }
      annualizedReturns.add(calculateAnnualizedReturns(LocalDate.parse(args[1]), trade,
          getOpeningPriceOnStartDate(candles), getClosingPriceOnEndDate(candles)));
    }
    if (annualizedReturns.size() == 0) {
      return annualizedReturns;
    }
    annualizedReturns.sort(new Comparator<AnnualizedReturn>() {
      @Override
      public int compare(AnnualizedReturn a1, AnnualizedReturn a2) {
        return a2.getAnnualizedReturn().compareTo(a1.getAnnualizedReturn());
      }
    });
    return annualizedReturns;
  }

  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate, PortfolioTrade trade,
      Double buyPrice, Double sellPrice) {
    Double totalReturns = (sellPrice - buyPrice) / buyPrice;
    Double years = trade.getPurchaseDate().until(endDate, ChronoUnit.DAYS) / 365.24;
    Double annualizedReturns = Math.pow(1 + totalReturns, (1 / years)) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturns, totalReturns);
  }

  public static List<AnnualizedReturn> mainCalculateReturnsAfterRefactor(String[] args)
      throws Exception {
    PortfolioManager portfolioManager =
        PortfolioManagerFactory.getPortfolioManager(new RestTemplate());
    String file = args[0];
    LocalDate endDate = LocalDate.parse(args[1]);
    return portfolioManager.calculateAnnualizedReturn(readTradesFromJson(file), endDate);
  }

  public static void main(String[] args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
    ThreadContext.put("runId", UUID.randomUUID().toString());
    printJsonObject(mainReadFile(args));
    printJsonObject(mainReadQuotes(args));
    printJsonObject(mainCalculateSingleReturn(args));
    printJsonObject(mainCalculateReturnsAfterRefactor(args));
  }
}

