package pl.adriangrzebyk.trader.domain;

public class TradeOffer {
	private Player player;
	private StarSystem system;
	private Goods goods;
	private OfferType offerType;
	private int price;
	private int initiative;

	Goods getGoods() {
		return goods;
	}
	StarSystem getSystem() {
		return system;
	}

	boolean isBuy() {
		return offerType.equals(OfferType.BUY);
	}

	public int getPrice() {
		return price;
	}

	public int getInitiative() {
		return initiative;
	}

	private TradeOffer(){}

	public static class Builder {
		private Player player;
		private StarSystem system;
		private Goods goods;
		private OfferType offerType;
		private int price;
		private int initiative;

		static Builder getInstance() {
			return new Builder();
		}

		Builder byPlayer(Player player) {
			this.player = player;
			return this;
		}

		Builder inSystem(StarSystem system) {
			this.system = system;
			return this;
		}

		Builder forGoods(Goods goods) {
			this.goods = goods;
			return this;
		}

		Builder withOfferType(OfferType offerType) {
			this.offerType = offerType;
			return this;
		}

		Builder forPrice(int price) {
			this.price = price;
			return this;
		}

		Builder withInitiative(int initiative) {
			this.initiative = initiative;
			return this;
		}

		TradeOffer build() {
			TradeOffer tradeOffer = new TradeOffer();
			tradeOffer.offerType = this.offerType;
			tradeOffer.goods = this.goods;
			tradeOffer.player = this.player;
			tradeOffer.price = this.price;
			tradeOffer.system = this.system;
			tradeOffer.initiative = this.initiative;
			return tradeOffer;
		}
	}
}
