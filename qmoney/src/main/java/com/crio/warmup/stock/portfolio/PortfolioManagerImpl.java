
package com.crio.warmup.stock.portfolio;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {

  private RestTemplate restTemplate;
  private StockQuotesService stockQuotesService;

  // Caution: Do not delete or modify the constructor, or else your build will break!
  // This is absolutely necessary for backward compatibility
  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public PortfolioManagerImpl(StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
  }

  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  static Double getOpeningPriceOnStartDate(List<Candle> candles) {
    return candles.get(0).getOpen();
  }

  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
    return candles.get(candles.size() - 1).getClose();
  }

  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate, PortfolioTrade trade,
      Double buyPrice, Double sellPrice) {
    Double totalReturns = (sellPrice - buyPrice) / buyPrice;
    Double years = trade.getPurchaseDate().until(endDate, ChronoUnit.DAYS) / 365.24;
    Double annualizedReturns = Math.pow(1 + totalReturns, (1 / years)) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturns, totalReturns);
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,
      LocalDate endDate) throws StockQuoteServiceException {
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    for (PortfolioTrade trade : portfolioTrades) {
      if (endDate.isBefore(trade.getPurchaseDate())) {
        throw new RuntimeException();
      }
      List<Candle> candles = new ArrayList<>();
      try {
        candles = getStockQuote(trade.getSymbol(), trade.getPurchaseDate(), endDate);
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

  public List<Candle> getStockQuote(String symbol, LocalDate purchaseDate, LocalDate endDate)
      throws JsonProcessingException, StockQuoteServiceException {
    return stockQuotesService.getStockQuote(symbol, purchaseDate, endDate);
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(
      List<PortfolioTrade> portfolioTrades, LocalDate endDate, int numThreads)
      throws StockQuoteServiceException {
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    try {
      List<Future<AnnualizedReturn>> futureAnnualizedReturns = new ArrayList<>();
      for (PortfolioTrade portfolioTrade : portfolioTrades) {
        Future<AnnualizedReturn> futureAnnualisedReturn =
            executor.submit(() -> calculateAnnualizedReturn(portfolioTrade, endDate));
        futureAnnualizedReturns.add(futureAnnualisedReturn);
      }
      annualizedReturns = getAnnualizedReturns(futureAnnualizedReturns);
    } finally {
      executor.shutdown();
    }
    if (annualizedReturns.size() == 0) {
      return annualizedReturns;
    }
    annualizedReturns.sort(getComparator());
    return annualizedReturns;
  }

  private List<AnnualizedReturn> getAnnualizedReturns(
      List<Future<AnnualizedReturn>> futureAnnualizedReturns) throws StockQuoteServiceException {
    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    for (Future<AnnualizedReturn> futureAnnualizedReturn : futureAnnualizedReturns) {
      try {
        AnnualizedReturn annualizedReturn = futureAnnualizedReturn.get();
        if (annualizedReturn != null) {
          annualizedReturns.add(annualizedReturn);
        }
      } catch (InterruptedException | ExecutionException e) {
        throw new StockQuoteServiceException();
      }
    }
    return annualizedReturns;
  }

  private AnnualizedReturn calculateAnnualizedReturn(PortfolioTrade portfolioTrade,
      LocalDate endDate) throws JsonProcessingException, StockQuoteServiceException {
    if (endDate.isBefore(portfolioTrade.getPurchaseDate())) {
      throw new RuntimeException();
    }
    try {
      List<Candle> candles =
          getStockQuote(portfolioTrade.getSymbol(), portfolioTrade.getPurchaseDate(), endDate);
      return calculateAnnualizedReturns(endDate, portfolioTrade,
          getOpeningPriceOnStartDate(candles), getClosingPriceOnEndDate(candles));
    } catch (JsonProcessingException e) {
      return null;
    }
  }

}
