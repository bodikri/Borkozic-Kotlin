 package com.borkozic.util
 
 import java.util.Calendar
 import java.util.Date
 import java.util.TimeZone
 
 /**
  * Converts time in different formats to Delphi (Pascal) <code>TDateTime</code> and vice versa.
  * Take in mind that <code>TDateTime</code> is internally represented as <code>double</code>:
  *
  *  * The integral part of a TDateTime value is the number of days that have passed since
  * 12/30/1899. The fractional part of a TDateTime value is the time of day.
  * Following are some examples of TDateTime values and their corresponding dates and times:
  *  * 0 - 12/30/1899 0:00
  * 2.75 - 1/1/1900 18:00
  * -1.25 - 12/29/1899 6:00
  * 35065 - 1/1/1996 0:00
  *
  * @author Andrey Novikov
  */
 object TDateTime {
     /**
      * Converts conventional Unix milliseconds to TDateTime format
      *
      * @param time milliseconds since January 1, 1970
      * @return time in TDateTime format
      */
     @JvmStatic
     fun toDateTime(time: Long): Double {
         /*
          * 25569 = 2.0 + 70*365 + 70 / 4
          * 2.0		- 1/1/1900 0:00
          * 70*365	- days in 70 years
          * 70 / 4	- extra days in leap years
          */
         return 25569 + time / 86400000.0 // 24*60*60*1000
     }
 
     /**
      * Converts [java.util.Date] to TDateTime format
      *
      * @param date time in Date format
      * @return time in TDateTime format
      */
     @JvmStatic
     fun toDateTime(date: Date): Double {
         val zero = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
         //12/30/1899 0:00
         zero.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
         zero.clear(Calendar.MILLISECOND)
         val from = Calendar.getInstance()
         from.time = date
 
         return (from.timeInMillis - zero.timeInMillis) / 86400000.0
     }
 
     /**
      * Converts TDateTime time to conventional Unix milliseconds
      *
      * @param time in TDateTime format
      * @return time milliseconds since January 1, 1970
      */
     @JvmStatic
     fun fromDateTime(time: Double): Long {
         return Math.round((time - 25569) * 86400000) // 24*60*60*1000
     }
 
     /**
      * Converts TDateTime time to [java.util.Date]
      *
      * @param time in TDateTime format
      * @return [java.util.Date]
      */
     @JvmStatic
     fun dateFromDateTime(time: Double): Date {
         val cal = Calendar.getInstance()
         cal.timeInMillis = fromDateTime(time)
         return cal.time
     }
 }