package com.github.freeacs.tr069.base;

import com.github.freeacs.common.util.Cache;
import com.github.freeacs.common.util.CacheValue;
import com.github.freeacs.dbi.File;

import java.util.ArrayList;
import java.util.List;

public class BaseCache {
  private static final Cache cache = new Cache();

  /** 2 minutes. 2分钟。 */
  private static final int SESSIONDATA_CACHE_TIMEOUT = 3 * 60 * 1000;

  /** 10 minutes. 10分钟。 */
  private static final int FIRMWAREIMAGE_CACHE_TIMEOUT = 10 * 60 * 1000;

  private static final String SESSION_KEY = "SESSION";

  private static final String FIRMWAREIMAGE_KEY = "FIRMWARE";

  /** Clears all parts of the cache, except for sessiondata. 清除缓存的所有部分，除了sessiondata。 */
  public static void clearCache() {
    List<String> keyRemoveList = new ArrayList<>();
    for (Object key : cache.getMap().keySet()) {
      String keyStr = (String) key;
      if (!keyStr.contains(SESSION_KEY)) {
        keyRemoveList.add(keyStr);
      }
    }
    for (String key : keyRemoveList) {
      cache.remove(key);
    }
  }

  /**
   * Retrieves the current session data from the cache based on a key that identifies the client.
   * 根据标识客户端的键从缓存中检索当前会话数据。
   *
   * @param unitKey Can be either session id or unit id // 可以是会话id或设备id
   * @return SessionDataI
   */
  public static SessionDataI getSessionData(String unitKey) {
    String key = unitKey + SESSION_KEY;
    CacheValue cv = cache.get(key);
    if (cv != null) {
      return (SessionDataI) cv.getObject();
    } else {
      throw new BaseCacheException(key);
    }
  }

  /**
   * Puts the given session data into the cache with a key that identifies the client.
   * 将给定的会话数据放入缓存，使用标识客户端的键。
   *
   * @param unitKey Can be either session id or unit id // 可以是会话id或设备id
   * @param sessionData The session data to be stored in cache // 要存储在缓存中的会话数据
   */
  public static void putSessionData(String unitKey, SessionDataI sessionData) {
    if (sessionData != null) {
      String key = unitKey + SESSION_KEY;
      CacheValue cv = new CacheValue(sessionData, Cache.SESSION, SESSIONDATA_CACHE_TIMEOUT);
      cv.setCleanupNotifier(new SessionDataCacheCleanup(unitKey, sessionData));
      cache.put(key, cv);
    }
  }

  public static void removeSessionData(String unitKey) {
    String key = unitKey + SESSION_KEY;
    cache.remove(key);
  }

  public static File getFirmware(String firmwareName, String unittypeName) {
    String key = firmwareName + unittypeName + FIRMWAREIMAGE_KEY;
    CacheValue cv = cache.get(key);
    if (cv != null) {
      return (File) cv.getObject();
    } else {
      return null;
    }
  }

  public static void putFirmware(String firmwareName, String unittypeName, File firmware) {
    String key = firmwareName + unittypeName + FIRMWAREIMAGE_KEY;
    if (firmware != null) {
      CacheValue cv = new CacheValue(firmware, Cache.ABSOLUTE, FIRMWAREIMAGE_CACHE_TIMEOUT);
      cache.put(key, cv);
    }
  }
}
