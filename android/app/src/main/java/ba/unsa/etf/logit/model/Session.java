package ba.unsa.etf.logit.model;

import java.util.List;

public class Session {
    public String sid;
    public String sig;
    public String mid;
    public String master;
    public List<Attendance> attns;

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getMaster() {
        return master;
    }

    public void setMaster(String master) {
        this.master = master;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        this.sig = sig;
    }

    public List<Attendance> getAttns() {
        return attns;
    }

    public void setAttns(List<Attendance> attns) {
        this.attns = attns;
    }
}
