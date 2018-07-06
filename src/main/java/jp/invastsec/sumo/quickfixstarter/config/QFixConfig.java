package jp.invastsec.sumo.quickfixstarter.config;

import java.io.Serializable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix="my")
public class QFixConfig {

    public static final class Service implements Serializable{
        private static final long serialVersionUID = 1L;

        private Integer minStartRqtId = 111111111;
        private Integer maxStartRqtId = 999999999;

        private String wsuri;
        private String cfg;
        private String sessionID;
        private String userName;
        private String password;

        public String getCfg() {
            return cfg;
        }
        public void setCfg(String cfg) {
            this.cfg = cfg;
        }
        public String getUserName() {
            return userName;
        }
        public void setUserName(String userName) {
            this.userName = userName;
        }
        public String getPassword() {
            return password;
        }
        public void setPassword(String password) {
            this.password = password;
        }
        public Integer getMinStartRqtId() {
            return minStartRqtId;
        }
        public void setMinStartRqtId(Integer minStartRqtId) {
            this.minStartRqtId = minStartRqtId;
        }
        public Integer getMaxStartRqtId() {
            return maxStartRqtId;
        }
        public void setMaxStartRqtId(Integer maxStartRqtId) {
            this.maxStartRqtId = maxStartRqtId;
        }
        public String getSessionID() {
            return sessionID;
        }
        public void setSessionID(String sessionID) {
            this.sessionID = sessionID;
        }
        public String getWsuri() {
            return wsuri;
        }
        public void setWsuri(String wsuri) {
            this.wsuri = wsuri;
        }
    }

    private Service okcoin = new Service();

    public Service getOkcoin() {
        return okcoin;
    }

    public void setOkcoin(Service okcoin) {
        this.okcoin = okcoin;
    }
}
