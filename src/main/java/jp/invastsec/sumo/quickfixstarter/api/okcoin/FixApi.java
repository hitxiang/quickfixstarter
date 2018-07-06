package jp.invastsec.sumo.quickfixstarter.api.okcoin;

import java.util.concurrent.CountDownLatch;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Handler;
import org.apache.camel.Producer;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;
import org.apache.camel.component.quickfixj.QuickfixjEventCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.invastsec.sumo.quickfixstarter.config.QFixConfig;
import jp.invastsec.sumo.quickfixstarter.transform.QuickfixjEventJsonTransformer;

import quickfix.ConfigError;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.field.MsgType;
import quickfix.field.RawData;
import quickfix.fix44.Logon;
import quickfix.fix44.MarketDataRequest;

public class FixApi extends RouteBuilder {
    private static final Logger log = LoggerFactory.getLogger(FixApi.class);

    final CountDownLatch logoutLatch = new CountDownLatch(1);

    private CamelContext camelContext;

//  @Bean
//  String myBean() {
//      return "I'm Spring bean!";
//  }

    @Override
    public void configure() {
        final String tradeName = getOkcoinConfig().getCfg();

        camelContext = getContext();

        // from("timer:trigger")
        // .transform().simple("ref:myBean")
        // .to("log:out");

        try {
            from("quickfix:" + tradeName + "/inprocess.cfg")
                    .filter(PredicateBuilder.and(
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                    .isEqualTo(QuickfixjEventCategory.AdminMessageSent),
                            header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.LOGON)))
                    .bean(new CredentialInjector());

            from("quickfix:" + tradeName + "/inprocess.cfg")
                    .filter(header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                            .isEqualTo(QuickfixjEventCategory.SessionLogoff))
                    .bean(new CountDownLatchDecrementer("logout", logoutLatch));

            from("quickfix:" + tradeName + "/inprocess.cfg")
                    .filter(PredicateBuilder.or(
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                    .isEqualTo(QuickfixjEventCategory.AdminMessageSent),
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                    .isEqualTo(QuickfixjEventCategory.AppMessageSent),
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                    .isEqualTo(QuickfixjEventCategory.AdminMessageReceived),
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                    .isEqualTo(QuickfixjEventCategory.AppMessageReceived)))
                    .bean(new QuickfixjEventJsonTransformer()).to("log:routing");

            from("quickfix:" + tradeName + "/inprocess.cfg")
                    .filter(PredicateBuilder.and(
                            header(QuickfixjEndpoint.EVENT_CATEGORY_KEY)
                                    .isEqualTo(QuickfixjEventCategory.AdminMessageReceived),
                            header(QuickfixjEndpoint.MESSAGE_TYPE_KEY).isEqualTo(MsgType.LOGON)))
                    .bean(new LogonAuthenticator());

            from("quickfix:" + tradeName + "/inprocess.cfg").filter(routeBuilder
                    .header(QuickfixjEndpoint.EVENT_CATEGORY_KEY).isEqualTo(QuickfixjEventCategory.SessionLogon))
                    .bean(new SessionLogon());

            from("quickfix:" + tradeName + "/inprocess.cfg")
                    .filter(header(QuickfixjEndpoint.MESSAGE_TYPE_KEY)
                            .isEqualTo(MsgType.MARKET_DATA_SNAPSHOT_FULL_REFRESH))
                    .bean(new QuickfixjEventJsonTransformer())
                    .to("rabbitmq:rmq-srv/finance.quote")
            //.to("websocket://0.0.0.0:8082/jfix")
            ;

        } catch (ConfigError e) {
            e.printStackTrace();
        }
    }

    private static Integer curRqtId = null;

    private String generateRequestId() {
        QFixConfig.Service okcoinConfig = getOkcoinConfig();

        if (curRqtId == null) {
            curRqtId = okcoinConfig.getMinStartRqtId();
        }
        Integer ret = curRqtId++;
        if (curRqtId > okcoinConfig.getMaxStartRqtId())
            curRqtId = okcoinConfig.getMinStartRqtId();
        return ret.toString();
    }

    public static class LogonAuthenticator {
        @Handler
        public void authenticate(Exchange exchange) throws RejectLogon, CamelExchangeException, FieldNotFound {
            log.info("LogonAuthenticator Acceptor is logon for "
                    + exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY));
            Message message = exchange.getIn().getMandatoryBody(Message.class);
            if (message.isSetField(RawData.FIELD)) {
                log.info("LogonAuthenticator body: " + message.getString(RawData.FIELD));
            }
        }
    }

    public class SessionLogon {
        @Handler
        public void logon(Exchange exchange) throws RejectLogon, CamelExchangeException, FieldNotFound {

            log.info("logon is received!");

            MarketDataRequest marketDataRequest = new MarketDataRequest(new quickfix.field.MDReqID(generateRequestId()),
                    new quickfix.field.SubscriptionRequestType(
                            quickfix.field.SubscriptionRequestType.SNAPSHOT_PLUS_UPDATES),
                    new quickfix.field.MarketDepth(1));

            final char[] fields = new char[] { quickfix.field.MDEntryType.OPENING_PRICE,
                    quickfix.field.MDEntryType.CLOSING_PRICE, quickfix.field.MDEntryType.TRADING_SESSION_HIGH_PRICE,
                    quickfix.field.MDEntryType.TRADING_SESSION_LOW_PRICE,
                    quickfix.field.MDEntryType.TRADING_SESSION_VWAP_PRICE, quickfix.field.MDEntryType.TRADE_VOLUME };

            MarketDataRequest.NoMDEntryTypes noMDEntryTypes = new MarketDataRequest.NoMDEntryTypes();
            for (char f : fields) {
                noMDEntryTypes.set(new quickfix.field.MDEntryType(f));
                marketDataRequest.addGroup(noMDEntryTypes);
            }

            final String[] symbols = new String[] { "BTC/CNY" }; // , "LTC/CNY"
            // only 1

            MarketDataRequest.NoRelatedSym noRelatedSym = new MarketDataRequest.NoRelatedSym();
            for (String symbol : symbols) {
                noRelatedSym.setField(new quickfix.field.Symbol(symbol));
                marketDataRequest.addGroup(noRelatedSym);
            }

            marketDataRequest.setField(new quickfix.field.MDUpdateType(quickfix.field.MDUpdateType.FULL_REFRESH));

            // exchange.getOut().setBody(marketDataRequest);

            // above row can not worked.
            // below is create one new exchange messge and send messge to
            // server.
            send(marketDataRequest);
        }

    }

    private Endpoint requestEndpoint;
    private Producer requestProducer;

    public void send(Message message) {
        try {
            QFixConfig.Service okcoinConfig = getOkcoinConfig();

            if (requestEndpoint == null) {
                String marketUri = "quickfix:" + okcoinConfig.getCfg() + "/inprocess.cfg" + okcoinConfig.getSessionID();
                requestEndpoint = camelContext.getEndpoint(marketUri);
            }
            if (requestProducer == null) {
                requestProducer = requestEndpoint.createProducer();
            }
            Exchange requestExchange = requestEndpoint.createExchange(ExchangePattern.InOnly);
            requestExchange.getIn().setBody(message);
            requestProducer.process(requestExchange);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class CredentialInjector {
        @Handler
        public void inject(Exchange exchange) throws CamelExchangeException {
            QFixConfig.Service okcoinConfig = getOkcoinConfig();

            log.info("Injecting password into outgoing logon message");
            Message message = exchange.getIn().getMandatoryBody(Message.class);
            Logon logon = (Logon) message;
            String s;

            s = okcoinConfig.getUserName();
            if ((s != null) && (!s.isEmpty()))
                logon.setField(new quickfix.field.Username(s));

            s = okcoinConfig.getPassword();
            if ((s != null) && (!s.isEmpty()))
                logon.setField(new quickfix.field.Password(s));
        }
    }
}