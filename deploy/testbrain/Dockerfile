# 国内默认：DaoCloud 代理的 library/python，避免直连 docker.io 超时
ARG PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-slim-bookworm
FROM ${PYTHON_IMAGE}

WORKDIR /app

# Debian apt 国内源
RUN if [ -f /etc/apt/sources.list.d/debian.sources ]; then \
      sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources; \
    elif [ -f /etc/apt/sources.list ]; then \
      sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list; \
    fi

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl build-essential default-libmysqlclient-dev pkg-config \
    && rm -rf /var/lib/apt/lists/*

# 使用本机已下载的源码（deploy/testbrain/vendor/TestBrain），不再在构建时 git clone
COPY vendor/TestBrain/ /app/

COPY requirements-docker.txt /tmp/requirements-docker.txt
ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
RUN pip install --no-cache-dir -i ${PIP_INDEX_URL} --trusted-host mirrors.aliyun.com -r /tmp/requirements-docker.txt

# Overlay：Docker settings、ATP 契约 API、embedding 懒加载、vector_store 补丁
COPY overlay/ /app/
COPY entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh

ENV PYTHONUNBUFFERED=1
ENV DJANGO_SETTINGS_MODULE=config.settings_docker
ENV GIT_PYTHON_REFRESH=quiet

EXPOSE 8000
ENTRYPOINT ["/entrypoint.sh"]
