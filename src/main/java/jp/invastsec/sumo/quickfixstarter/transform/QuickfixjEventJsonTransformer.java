package jp.invastsec.sumo.quickfixstarter.transform;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.component.quickfixj.QuickfixjEndpoint;

import quickfix.ConfigError;
import quickfix.DataDictionary;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;

public class QuickfixjEventJsonTransformer {
    private final QuickfixjMessageJsonTransformer renderer;

    public QuickfixjEventJsonTransformer() throws ConfigError {
        renderer = new QuickfixjMessageJsonTransformer();
    }

    @Handler
    public String transform(Exchange exchange) {
        SessionID sessionID = exchange.getIn().getHeader(QuickfixjEndpoint.SESSION_ID_KEY, SessionID.class);
        Session session = Session.lookupSession(sessionID);
        DataDictionary dataDictionary = session.getDataDictionary();

        if (dataDictionary == null) {
            throw new IllegalStateException("No Data Dictionary. Exchange must reference an existing session");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\"event\": {\n");

        org.apache.camel.Message in = exchange.getIn();
        for (String key : in.getHeaders().keySet()) {
            sb.append("  \"").append(key).append("\": ").append(in.getHeader(key)).append(",\n");
        }

        sb.append(renderer.transform(in.getBody(Message.class), "  ", dataDictionary)).append("\n");
        sb.append("}\n");
        return sb.toString();
    }
}