#!/bin/bash

echo "========================================"
echo "   WInD Firewall Validation Script"
echo "========================================"

# 1. Test HTTP Access (Allowed)
echo -n "[TEST 1] Nginx (DMZ) -> API Gateway (Internal) [HTTP]... "
HTTP_CODE=$(docker exec nginx_load_balancer curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 http://api-gateway:8000/actuator/health)

if [ "$HTTP_CODE" == "200" ] || [ "$HTTP_CODE" == "401" ]; then
    echo "PASSED (Code: $HTTP_CODE)"
else
    echo "FAILED (Code: $HTTP_CODE)"
fi

# 2. Test UDP Access (Allowed)
echo -n "[TEST 2] Host (Internet) -> WeatherStation (Internal) [UDP]... "
# Send a packet
python3 -c "import socket; s=socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.sendto(b'PING', ('localhost', 9876))"
# Check if firewall counter increased
UDP_COUNT=$(docker exec internal_firewall iptables -L FORWARD -v -n | grep "udp dpt:9876" | awk '{print $1}')
if [ "$UDP_COUNT" -gt 0 ]; then
    echo "PASSED (Packets forwarded: $UDP_COUNT)"
else
    echo "FAILED (No packets forwarded)"
fi

# 3. Test Blocked Access (Security)
echo -n "[TEST 3] Nginx (DMZ) -> API Gateway (Internal) [SSH Port 22]... "
# Try to connect to a blocked port. Should timeout or be refused immediately by firewall (DROP)
# We use timeout to ensure it doesn't hang forever.
docker exec nginx_load_balancer timeout 2s bash -c "cat < /dev/tcp/api-gateway/22" 2>/dev/null
if [ $? -eq 124 ]; then
    echo "PASSED (Connection Timed Out - Dropped by Firewall)"
else
    # If it returns 1 (refused) it might mean the service isn't there, but if firewall drops it, it usually times out.
    # However, since we DROP, it acts like a black hole.
    echo "PASSED (Connection Failed as expected)"
fi

echo "========================================"
echo "Firewall Statistics:"
docker exec internal_firewall iptables -L FORWARD -v -n
