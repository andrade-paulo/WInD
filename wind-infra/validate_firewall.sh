#!/bin/bash

echo "========================================"
echo "   WInD Firewall Validation Script"
echo "========================================"

# Test valid HTTP Access
echo -n "[TEST 1] Nginx (DMZ) -> API Gateway (Internal) [HTTP]... "
HTTP_CODE=$(docker exec nginx_load_balancer curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 http://api-gateway:8000/actuator/health)

if [ "$HTTP_CODE" == "200" ] || [ "$HTTP_CODE" == "401" ]; then
    echo "PASSED (Code: $HTTP_CODE)"
else
    echo "FAILED (Code: $HTTP_CODE)"
fi

# Test valid UDP Access
echo -n "[TEST 2] Host (Internet) -> WeatherStation (Internal) [UDP]... "
python3 -c "import socket; s=socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.sendto(b'PING', ('localhost', 9876))"

# Check if firewall counter increased
UDP_COUNT=$(docker exec internal_firewall iptables -L FORWARD -v -n | grep "udp dpt:9876" | awk '{print $1}')
if [ "$UDP_COUNT" -gt 0 ]; then
    echo "PASSED (Packets forwarded: $UDP_COUNT)"
else
    echo "FAILED (No packets forwarded)"
fi


# Test invalid SSH access
echo -n "[TEST 3] Nginx (DMZ) -> API Gateway (Internal) [SSH Port 22]... "

docker exec nginx_load_balancer timeout 3s bash -c "cat < /dev/tcp/api-gateway/22" 2>/dev/null
if [ $? -eq 124 ]; then
    echo "PASSED (Connection Timed Out - Dropped by Firewall)"
elif [ $? -eq 0 ]; then
    echo "FAILED (Connection Established)"
else
    echo "FAILED (Unexpected Error)"
fi



# Test host HTTP to application server (access should be denied)
echo -n "[TEST 4] Host (Internet) -> Application Server (Internal) [HTTP Port 8080]... "
timeout 3s bash -c "cat < /dev/tcp/app_server/8080" 2>/dev/null
if [ $? -eq 124 ]; then
    echo "PASSED (Connection Timed Out - Dropped by Firewall)"
elif [ $? -eq 0 ]; then
    echo "FAILED (Connection Established)"
else
    echo "FAILED (Unexpected Error)"
fi


echo "========================================"
echo "Firewall Statistics:"
docker exec internal_firewall iptables -L FORWARD -v -n
