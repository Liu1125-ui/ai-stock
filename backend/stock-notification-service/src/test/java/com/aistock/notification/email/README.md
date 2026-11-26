# 邮件隔离测试

mysql标签测试只允许stock_notification_test，并在每个夹具中清理该专用测试库的发送记录，默认串行执行，不连接业务库。测试transport仅在测试类中构造，不用于运行系统或声称邮件已送达。

运行scripts/start-auth-test-db.ps1后，scripts/check-auth-task1.ps1执行两服务独立MySQL测试及全后端回归。
