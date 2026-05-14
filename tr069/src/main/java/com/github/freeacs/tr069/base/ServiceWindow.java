package com.github.freeacs.tr069.base;

import com.github.freeacs.common.util.TimeWindow;
import com.github.freeacs.dbi.util.SystemConstants;
import com.github.freeacs.dbi.util.SystemParameters;
import lombok.extern.slf4j.Slf4j;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Random;

@Slf4j
public class ServiceWindow {
  /** For random distribution of PII within the ServiceWindow. 用于在服务窗口内随机分布PII。 */
  private static final Random random = new Random(System.currentTimeMillis());

  private final TimeWindow timeWindow;
  private final long currentTms;
  private final ACSParameters ACSParameters;
  private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  public ServiceWindow(SessionDataI sessionData, boolean disruptive) {
    this.currentTms = System.currentTimeMillis();
    this.ACSParameters = sessionData.getAcsParameters();
    if (disruptive) {
      timeWindow =
          new TimeWindow(ACSParameters.getValue(SystemParameters.SERVICE_WINDOW_DISRUPTIVE));
    } else {
      timeWindow = new TimeWindow(ACSParameters.getValue(SystemParameters.SERVICE_WINDOW_REGULAR));
    }
  }

  private boolean isEnabled() {
    String enable = ACSParameters.getValue(SystemParameters.SERVICE_WINDOW_ENABLE);
    return enable == null || (!"0".equals(enable) && !"false".equalsIgnoreCase(enable));
  }

  /** Return the number of provisionings per week. Default value is once per day. 返回每周的配置次数。默认值为每天一次。 */
  private float findFrequency() {
    String freq = ACSParameters.getValue(SystemParameters.SERVICE_WINDOW_FREQUENCY);
    float freqFloat = (float) timeWindow.getWeeklyLength() / timeWindow.getDailyLength();
    if (freq != null) {
      try {
        freqFloat = Float.parseFloat(freq);
      } catch (Throwable t) {
      }
    }
    return freqFloat;
  }

  private float findSpread() {
    float freqFloat = (float) SystemConstants.DEFAULT_SERVICEWINDOW_SPREAD_INT / 100f;
    String freq = ACSParameters.getValue(SystemParameters.SERVICE_WINDOW_SPREAD);
    if (freq != null) {
      try {
        freqFloat = Float.valueOf(freq) / 100f;
      } catch (Throwable t) {
      }
    }
    return freqFloat;
  }

  public boolean isWithin() {
    return isWithin(currentTms);
  }

  /**
   * Return false if this time stamp is outside the service-window.
   * 如果此时间戳在服务窗口之外，则返回false。
   *
   * @param tms
   * @return
   */
  private boolean isWithin(long tms) {
    return !isEnabled() || timeWindow.isWithinTimeWindow(tms);
  }

  /**
   * The repeatable job will maintain a fixed interval between each run, but it must still obey the
   * service window. Thus each time a repeatable job hits outside the service window, that job
   * execution is skipped. Example:
   * 可重复作业将在每次运行之间保持固定间隔，但仍必须遵守服务窗口。因此，每当可重复作业落在服务窗口之外时，该作业执行将被跳过。示例：
   *
   * <p>SW: mo-su:0800-0900 Job: repeat every 1200 sec
   * 服务窗口：周一至周日:0800-0900 作业：每1200秒重复一次
   *
   * <p>CPE contacts server at 0801 (first time after job has been created). The job executes at
   * 0801, 0821, 0841 on Monday. Then it continues to execute at 0801, 0821 and 0841 on Tuesday. All
   * the in-between job executions are skipped.
   * CPE在0801联系服务器（创建作业后的第一次）。作业在周一的0801、0821、0841执行。然后在周二的0801、0821和0841继续执行。所有中间的作业执行都被跳过。
   *
   * <p>This principle makes it possible to create an interval of the job and a service window, such
   * that the job will skip several (or all) possible service windows. This is unfortunate, but it
   * seems more intuitive to keep the intervals fixed in real-time, than fixed through
   * service-window-time.
   * 这个原则使得创建作业间隔和服务窗口成为可能，使得作业将跳过几个（或所有）可能的服务窗口。这很遗憾，但保持间隔在实时中固定似乎比在服务窗口时间中固定更直观。
   *
   * @return
   */
  long calculateNextRepeatableTms(Long lastRunTms, long fixedInterval) {
    long nextRunTms = currentTms;
    if (fixedInterval == 0) {
      log.debug("Interval is 0, NRT is now!");
      return nextRunTms;
    }
    long fixedIntervalMs = fixedInterval * 1000L;
    if (lastRunTms == null) { // this subtraction will be canceled below // 此减法将在下面被抵消
      log.debug("LRT = null");
      nextRunTms -= fixedIntervalMs;
    } else {
      log.debug("LRT = " + convert(lastRunTms));
      nextRunTms = lastRunTms;
    }
    if (currentTms - nextRunTms > 0) {
      long noIntervals = (currentTms - nextRunTms) / fixedIntervalMs;
      nextRunTms += (noIntervals - 1) * fixedIntervalMs;
    }

    do {
      nextRunTms += fixedIntervalMs;
      log.debug("Suggested NRT = " + convert(nextRunTms));
      long timeToNRT = nextRunTms - currentTms;
      if (timeToNRT
          < -PIIDecision.MINIMUM_PII
              * 1000L) { // do not test old tms, but let tms close to current time pass
        // 不测试旧的时间戳，但让接近当前时间的时间戳通过
        log.debug("Suggested NRT was rejected as too old (timeToNRT = " + timeToNRT / 1000 + " sec)");
        continue;
      }
      if (timeWindow.isWithinTimeWindow(nextRunTms)) {
        log.debug("Suggested NRT was accepted");
        break;
      } else {
        log.debug("Suggested NRT was rejected as outside SW");
      }
    } while (true);
    return nextRunTms;
  }

  /**
   * Calculates the PeriodicInformInterval (in seconds) in standard way following these rules or
   * guidelines: If Enabled it will be calculated according these rules a) PII must be within
   * ServiceWindow b) PII must be set according to the Frequency -> interval c) PII must be spread
   * according to Spread d) PII can be minimum 31 seconds If Not Enabled, it will be calculated
   * according to b) and d) only
   * 以标准方式计算PeriodicInformInterval（以秒为单位），遵循以下规则或指南：如果启用，将根据以下规则计算 a) PII必须在ServiceWindow内 b) PII必须根据Frequency设置 -> 间隔 c) PII必须根据Spread分散 d) PII最小可以为31秒 如果未启用，将仅根据b)和d)计算
   *
   * @return
   */
  long calculateStdPII() {
    if (isEnabled()) {
      long nextPIITms = 0;
      long nextInterval = calculateNextInterval();

      /*
       * If we're inside the ServiceWindow right now, calculate how much
       * is left of the ServiceWindow (leftOfTW). If nextInterval is
       * more into the future than can fit within leftOfTW, then subtract
       * all of leftOfTW from nextInterval. Otherwise next PeriodicInformInterval
       * will fit within this current Time-window (ServiceWindow) and we can just
       * add nextInterval to current time stamp.
       * 如果我们现在在ServiceWindow内，计算ServiceWindow还剩多少（leftOfTW）。
       * 如果nextInterval比leftOfTW能容纳的未来时间更长，则从nextInterval中减去所有leftOfTW。
       * 否则next PeriodicInformInterval将适合当前时间窗口（ServiceWindow），我们可以直接将nextInterval加到当前时间戳。
       */
      if (timeWindow.isWithinTimeWindow(currentTms)) {
        long leftOfTW =
            timeWindow.getPreviousStartTms(currentTms) + timeWindow.getDailyLength() - currentTms;
        if (nextInterval > leftOfTW) {
          nextInterval -= leftOfTW;
        } else {
          nextPIITms = currentTms + nextInterval;
        } // We have our final time stamp.. // 我们有了最终的时间戳..
      }

      /*
       * Check if nextInterval fits within the next TimeWindow. Continue
       * until nextInterval fits, and then calculate next Periodic Inform
       * Interval timestamp.
       * 检查nextInterval是否适合下一个TimeWindow。继续直到nextInterval适合，然后计算下一个Periodic Inform Interval时间戳。
       */
      if (nextPIITms == 0) {
        nextPIITms = timeWindow.getNextStartTms(currentTms);
        while (timeWindow.getDailyLength() < nextInterval) {
          nextInterval -= timeWindow.getDailyLength();
          nextPIITms = timeWindow.getNextStartTms(nextPIITms);
        }
        nextPIITms += nextInterval;
      }

      // Convert from timestamp (ms) back to Periodic Inform Interval (seconds
      // til next provisioning).
      // 从时间戳（毫秒）转换回Periodic Inform Interval（到下一次配置的秒数）。
      long nextPII = (nextPIITms - currentTms) / 1000;
      log.debug("Standard PeriodicInformInterval calculated to "
              + nextPII
              + "("
              + convert(nextPIITms)
              + ") (TimeWindow is : "
              + timeWindow
              + ")");
      if (nextPII < PIIDecision.MINIMUM_PII) {
        log.debug("Standard PeriodicInformInterval was calculated too low, changed to "
                + PIIDecision.MINIMUM_PII);
        nextPII = PIIDecision.MINIMUM_PII;
      }
      // Return as seconds // 以秒为单位返回
      return nextPII;
    } else {
      // Make sure frequency is set to once pr day // 确保频率设置为每天一次
      String freq = ACSParameters.getValue(SystemParameters.SERVICE_WINDOW_FREQUENCY);
      float freqFloat = 7; // default - once pr day // 默认 - 每天一次
      if (freq != null) {
        try {
          freqFloat = Float.parseFloat(freq);
        } catch (Throwable t) {
        }
      }
      // Find the nextInterval (calculations are all in seconds) // 找到nextInterval（所有计算都以秒为单位）
      long nextPII = (long) ((float) (7 * 24 * 3600) / freqFloat);
      long nextPIITms = (currentTms + nextPII * 1000L) / 1000;
      log.debug("Standard PeriodicInformInterval (SW disabled) calculated to "
              + nextPII
              + "("
              + convert(nextPIITms)
              + ") (TimeWindow is : "
              + timeWindow
              + ")");
      // make sure it is above 30 seconds // 确保大于30秒
      if (nextPII < PIIDecision.MINIMUM_PII) {
        log.debug("Standard PeriodicInformInterval (SW disabled) was calculated too low, set to "
                + PIIDecision.MINIMUM_PII);
        nextPII = PIIDecision.MINIMUM_PII;
      }
      return nextPII;
    }
  }

  /**
   * This method will calculate the next interval to a provisioning. However, it is important to
   * understand that this interval relates to the time windows defined be this ServiceWindow class.
   * Here's an example to explain the issue:
   * 此方法将计算到下一次配置的间隔。但是，重要的是要理解此间隔与此ServiceWindow类定义的时间窗口相关。这里有一个示例来解释这个问题：
   *
   * <p>A ServiceWindow can be defined as "mo-we:0800-1200". In this case the total number of hours
   * available for provisioning during a week is 4h*3days=12h Furthermore, assume that the frequency
   * is set to 2. This means that there should be a provisioning every 6h. This number is
   * represented by defaultInterval in the code. Assuming that there is a provisioning at 8am Monday
   * morning, you must understand that the next should not happen before 10am Tuesday morning, but
   * the number returned from this method is still 6h (or rather translated to milliseconds).
   * ServiceWindow可以定义为"mo-we:0800-1200"。在这种情况下，一周内可用于配置的总小时数为4小时*3天=12小时。此外，假设频率设置为2。这意味着应该每6小时进行一次配置。这个数字在代码中表示为defaultInterval。假设周一早上8点有一次配置，你必须理解下一次不应该在周二早上10点之前发生，但此方法返回的数字仍然是6小时（或者更准确地说是转换为毫秒）。
   *
   * <p>Additionally we have something called SpreadFactor. This is a factor from 0-100%
   * (represented as a float going from 0 to 1), which will spread the interval accordingly. We
   * calculate spread
   * 此外，我们有一个叫做SpreadFactor的东西。这是一个从0-100%的因子（表示为从0到1的浮点数），它将相应地分散间隔。我们计算spread
   *
   * <p>Spread is calculated like this:
   * Spread的计算方式如下：
   *
   * <p>Spread = random(2*DefaultInterval*SpreadFactor)-DefaultInterval*SpreadFactor
   * Spread = random(2*DefaultInterval*SpreadFactor)-DefaultInterval*SpreadFactor
   *
   * <p>If SpreadFactor is 50% and DefaultInterval is 6h, the Spread can now range from -3h to 3h.
   * This Spread is added to Default Interval to give the FinalInterval.
   * 如果SpreadFactor是50%且DefaultInterval是6小时，Spread现在可以从-3小时到3小时。这个Spread被添加到Default Interval以给出FinalInterval。
   *
   * @return
   */
  private long calculateNextInterval() {
    // The spread of the interval. Default is 0.5 (= 50%). // 间隔的分散。默认为0.5（= 50%）。
    float spreadFactor = findSpread();
    // find frequency, default is once a day (expressed as 7 if service window
    // defines all days of the week (= mo-su))
    // 查找频率，默认为每天一次（如果服务窗口定义了一周的所有天（= mo-su），则表示为7）
    float frequency = findFrequency();
    // The length (in ms) of a time-window
    // The freq-time-window is the time allowed for provisioning during a whole week
    // divided by the frequency of provisionings pr week. Thus 24*7h weekly prov.
    // time and frequency=7 will give 24h time-window.
    // 时间窗口的长度（以毫秒为单位）
    // freq-time-window是一周内允许配置的总时间除以每周配置的频率。因此24*7小时的每周配置时间和频率=7将给出24小时的时间窗口。
    long defaultInterval = (long) ((float) timeWindow.getWeeklyLength() / frequency);
    if (spreadFactor == 0f) { // Special code to treat 0 spread (random function cannot handle 0)
      // 处理0分散的特殊代码（随机函数无法处理0）
      log.debug("DefaultInterval calculated to "
              + defaultInterval / 1000
              + " seconds - spreadfactor is 0");
    } else {
      int ds = (int) (defaultInterval * spreadFactor);
      defaultInterval = defaultInterval + random.nextInt(ds * 2) - ds;
      log.debug("DefaultInterval calculated to "
              + defaultInterval / 1000
              + " seconds - spreadfactor is "
              + spreadFactor * 100);
    }
    return defaultInterval;
  }

  long getCurrentTms() {
    return currentTms;
  }

  static String convert(Long tms) {
    return sdf.format(new Date(tms));
    //		return String.format("%1$tF %1$tR", tms);
  }

  TimeWindow getTimeWindow() {
    return timeWindow;
  }
}
