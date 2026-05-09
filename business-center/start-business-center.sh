#!/usr/bin/env bash

export BUSINESS_CENTER_PORT=8091
export BUSINESS_CENTER_MYSQL_DRIVER=com.mysql.cj.jdbc.Driver
export BUSINESS_CENTER_MYSQL_URL='jdbc:mysql://127.0.0.1:3307/business_center?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
export BUSINESS_CENTER_MYSQL_USERNAME=root
export BUSINESS_CENTER_MYSQL_PASSWORD=123456

mvn spring-boot:run
