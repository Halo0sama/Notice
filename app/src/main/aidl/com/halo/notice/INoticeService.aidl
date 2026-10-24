package com.halo.notice;
interface INoticeService {
    String channels(String pkg, int uid) = 0;
    String change(String pkg, int uid, String channelId, int expectedImportance, int importance) = 1;
    void destroy() = 16777114;
}
