package dev.adriangrzebyk.trader.domain.marketsituation;

import dev.adriangrzebyk.trader.domain.Goods;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
public class MarketSituation {
    @Id
    private UUID uuid;
    private Integer turn;
    private String system;
    private String goods;
    private Integer price;
    private Integer sdIndicator;
}
