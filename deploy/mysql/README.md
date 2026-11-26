# MySQL 本机初始化

`01-databases.sh` 在新建 MySQL 数据卷时创建五库与各自账号，密码来自忽略提交的根目录 `.env`。账号只拥有本库的数据读写及当前迁移所需 CREATE/ALTER/INDEX/REFERENCES 权限。脚本仅接受安全字母数字密码，不拼入任意 SQL。再次启动保留已有数据与账号；更改 `.env` 不会自动改已有账号密码。验证用 `tests/check-compose.ps1`，不能通过删除数据卷重置现有业务数据。
