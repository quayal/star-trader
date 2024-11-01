package dev.adriangrzebyk.trader.domain.tradeoffer;

import dev.adriangrzebyk.trader.domain.Goods;
import dev.adriangrzebyk.trader.domain.StarSystem;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

@RestController
@RequestMapping("trade-offer")
class TradeOfferController {

    private final TradeOfferFacade tradeOfferFacade;

    TradeOfferController(TradeOfferFacade facade) {
        this.tradeOfferFacade = facade;
    }


    @PostMapping(value = "/register", consumes = APPLICATION_JSON_VALUE, produces = TEXT_PLAIN_VALUE)
    String registerTradeOffer(@RequestBody TradeOffer tradeOffer) {
        return String.valueOf(tradeOfferFacade.registerTradeOffer(tradeOffer));
    }

    @GetMapping(value = "/process", produces = APPLICATION_JSON_VALUE)
    TransactionTradeOffer processGoodsInStarSystem(@RequestParam int turn, @RequestParam Goods goods, @RequestParam StarSystem starSystem) {
        return tradeOfferFacade.getWinningOffer(turn, goods, starSystem);
    }
}
