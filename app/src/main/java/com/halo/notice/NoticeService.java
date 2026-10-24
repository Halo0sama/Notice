package com.halo.notice;
import android.content.Context;
import org.json.JSONObject;
public final class NoticeService extends INoticeService.Stub {
    private NotificationEngine engine;
    public NoticeService() {}
    public NoticeService(Context context) {}
    private NotificationEngine engine() throws Exception { if(engine==null) engine=new NotificationEngine(); return engine; }
    private String error(Exception e) { return "{\"error\":"+JSONObject.quote(e.getMessage()==null?"系统服务调用失败":e.getMessage())+"}"; }
    @Override public synchronized String channels(String pkg,int uid) { try {return engine().channels(pkg,uid);}catch(Exception e){return error(e);} }
    @Override public synchronized String change(String pkg,int uid,String id,int expected,int importance) { try{return engine().change(pkg,uid,id,expected,importance);}catch(Exception e){return error(e);} }
    @Override public void destroy(){System.exit(0);}
}
