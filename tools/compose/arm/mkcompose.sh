cat >/root/reinstall_compose_v1.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

# --- 基本参数 ---
VENV_DIR="/opt/dcenv"
BIN_LINK="/usr/local/bin/docker-compose"
COMPOSE_VER="1.29.2"

echo "==> 1. 清理残留并准备依赖（Python3 / pip / 构建依赖）"
yum install -y python3 python3-pip python3-devel gcc libffi-devel openssl-devel || true

echo "==> 2. 重新创建可复制的虚拟环境（--copies 避免绝对路径软链）"
rm -rf "$VENV_DIR"
python3 -m venv --copies "$VENV_DIR"

echo "==> 3. 激活 venv 并升级 pip 工具链"
source "$VENV_DIR/bin/activate"
python -m pip install -U "pip>=21.3" "setuptools>=65,<70" wheel

# 如需国内源，加一行（可选，取消 # 生效）：
# python -m pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple

echo "==> 4. 先装 PyYAML 的二进制轮子，避免源码编译"
PIP_ONLY_BINARY=:all: python -m pip install --prefer-binary "PyYAML<6,>=5.4.1"

echo "==> 5. 安装 docker-compose v${COMPOSE_VER}"
python -m pip install "docker-compose==${COMPOSE_VER}"

echo "==> 6. 退出 venv，并创建全局软链"
set +u
deactivate 2>/dev/null || true
set -u

install -d /usr/local/bin
ln -sf /opt/dcenv/bin/docker-compose /usr/local/bin/docker-compose
docker-compose version

echo "==> 7. 校验版本："
"${BIN_LINK}" version

echo "==> 8. 打包备份，可用于离线恢复"
tar -C /opt -czf "/root/docker-compose-venv-arm64-${COMPOSE_VER}.tgz" "$(basename "$VENV_DIR")"
echo "==> 完成。备份文件：/root/docker-compose-venv-arm64-${COMPOSE_VER}.tgz"
EOF

chmod +x /root/reinstall_compose_v1.sh
bash /root/reinstall_compose_v1.sh

