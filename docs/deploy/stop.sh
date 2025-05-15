#!/bin/bash
##Program
#       stop
#History:
#2025/05/15     junfenghe.cloud@qq.com  version:0.0.1

path=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:~/bin
export path

kill -9 $( cat pid )

echo big health server stopped.