package dev.adriangrzebyk.trader;

import dev.adriangrzebyk.trader.domain.tradeoffer.TradeOfferFacade;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class TradeController {
    private TradeOfferFacade tradeOfferFacade;
}
