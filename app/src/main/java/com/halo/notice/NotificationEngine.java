package com.halo.notice;

import android.app.NotificationChannel;
import android.os.IBinder;
import android.os.Binder;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;

/** Only constructed in Shizuku's shell process. Never hard-code Binder transaction numbers. */
public final class NotificationEngine {
    private final Object manager;
    private final Class<?> api;
    public NotificationEngine() throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "notification");
        api = Class.forName("android.app.INotificationManager");
        manager = Class.forName("android.app.INotificationManager$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
    private Object invoke(String name, Object... args) throws Exception {
        for (Method m: api.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try { return m.invoke(manager, args); }
                catch (InvocationTargetException e) { throw new Exception(e.getTargetException().toString()); }
            }
        }
        throw new NoSuchMethodException("系统未提供 " + name);
    }
    @SuppressWarnings("unchecked")
    private List<NotificationChannel> list(String pkg, int uid) throws Exception {
        Object slice = invoke("getNotificationChannelsForPackage", pkg, uid, false);
        return (List<NotificationChannel>) slice.getClass().getMethod("getList").invoke(slice);
    }
    private void checkIdentity(String pkg, int uid) throws Exception {
        if (pkg == null || pkg.isEmpty() || uid < 0) throw new IllegalArgumentException("应用标识无效");
        Object pm = Class.forName("android.content.pm.IPackageManager$Stub").getMethod("asInterface", IBinder.class).invoke(null,
            Class.forName("android.os.ServiceManager").getMethod("getService", String.class).invoke(null, "package"));
        Class<?> pmi = Class.forName("android.content.pm.IPackageManager");
        for (Method m: pmi.getMethods()) if (m.getName().equals("getPackageUid") && m.getParameterCount()==3) {
            Object flags = m.getParameterTypes()[1] == long.class ? (Object) Long.valueOf(0) : Integer.valueOf(0);
            int actual = (int)m.invoke(pm, pkg, flags, uid / 100000);
            if (actual != uid) throw new IllegalStateException("应用已更换或卸载，请重新扫描");
            return;
        }
        throw new NoSuchMethodException("getPackageUid");
    }
    public String channels(String pkg, int uid) throws Exception {
        long identity = Binder.clearCallingIdentity();
        try {
            checkIdentity(pkg,uid);
            JSONArray out = new JSONArray();
            Map<String,Boolean> groups = new HashMap<>();
            try {
                Object slice = invoke("getNotificationChannelGroupsForPackage",pkg,uid,false);
                for(Object group:(List<?>)slice.getClass().getMethod("getList").invoke(slice)) {
                    String id = (String)group.getClass().getMethod("getId").invoke(group);
                    groups.put(id, (boolean)group.getClass().getMethod("isBlocked").invoke(group));
                }
            } catch (Exception ignored) {}
            boolean appEnabled = (boolean)invoke("areNotificationsEnabledForPackage",pkg,uid);
            for(NotificationChannel c:list(pkg,uid)) {
                boolean critical = false;
                try { critical = (boolean) NotificationChannel.class.getMethod("isImportanceLockedByCriticalDeviceFunction").invoke(c); } catch(Exception ignored) {}
                out.put(new JSONObject().put("id",c.getId()).put("name",c.getName()).put("description",c.getDescription())
                    .put("importance",c.getImportance()).put("group",c.getGroup()).put("groupBlocked",groups.getOrDefault(c.getGroup(),false))
                    .put("appEnabled",appEnabled).put("critical",critical).put("sound",c.getSound()!=null).put("vibration",c.shouldVibrate())
                    .put("badge",c.canShowBadge()));
            }
            return out.toString();
        } finally { Binder.restoreCallingIdentity(identity); }
    }
    public synchronized String change(String pkg,int uid,String id,int expected,int importance) throws Exception {
        if (importance < 0 || importance > 5) throw new IllegalArgumentException("无效的通知级别");
        long identity = Binder.clearCallingIdentity();
        try {
            checkIdentity(pkg,uid);
            NotificationChannel found = null;
            for (NotificationChannel c:list(pkg,uid)) if(c.getId().equals(id)) found=c;
            if(found==null) throw new IllegalStateException("通道已不存在，请重新扫描");
            if(found.getImportance()!=expected) throw new IllegalStateException("设置已被其他程序修改，请重新扫描后重试");
            found.setImportance(importance);
            invoke("updateNotificationChannelForPackage",pkg,uid,found);
            for(NotificationChannel after:list(pkg,uid)) if(after.getId().equals(id)) {
                if(after.getImportance()!=importance) throw new IllegalStateException("系统未接受修改，此通道可能受保护");
                return new JSONObject().put("verified",true).put("importance",after.getImportance()).toString();
            }
            throw new IllegalStateException("修改后无法回读通道");
        } finally { Binder.restoreCallingIdentity(identity); }
    }
}
