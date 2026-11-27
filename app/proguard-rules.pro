# Keep ZXing QR code encoder classes
-keep class com.google.zxing.** { *; }

# Keep ViewModel & BroadcastReceiver entry points
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class com.rohan.debtsync.reminder.EmiReminderReceiver
-keep class com.rohan.debtsync.sms.EmiSmsReceiver
