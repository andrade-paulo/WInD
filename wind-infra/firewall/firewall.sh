#!/bin/sh

# Enable IP Forwarding (Handled by Docker sysctls now)
# echo 1 > /proc/sys/net/ipv4/ip_forward

# Flush existing rules
iptables -F
iptables -t nat -F
iptables -X

# Default Policies
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# Allow Loopback
iptables -A INPUT -i lo -j ACCEPT

# Allow ICMP (Ping) for debugging
iptables -A INPUT -p icmp -j ACCEPT
iptables -A FORWARD -p icmp -j ACCEPT

# ==========================================
# NAT RULES (Port Forwarding)
# ==========================================

# 0. SNAT (Masquerade) for traffic entering Internal Net
# This ensures the return traffic goes back through the Firewall, not the Docker Host gateway.
iptables -t nat -A POSTROUTING -d 172.21.0.0/24 -j MASQUERADE

# 1. HTTP: Nginx (DMZ) -> API Gateway (Internal)
# Traffic destined to Firewall IP (172.20.0.5) on port 8000
iptables -t nat -A PREROUTING -d 172.20.0.5 -p tcp --dport 8000 -j DNAT --to-destination 172.21.0.10:8000

# 2. UDP: Microcontrollers (Internet) -> WeatherStation (Internal)
# Traffic destined to Firewall IP (172.20.0.5) on port 9876
iptables -t nat -A PREROUTING -d 172.20.0.5 -p udp --dport 9876 -j DNAT --to-destination 172.21.0.14:9876

# 3. Masquerade outbound traffic from Internal Net
iptables -t nat -A POSTROUTING -s 172.21.0.0/24 -o eth0 -j MASQUERADE

# ==========================================
# FORWARDING RULES
# ==========================================

# 1. Allow Nginx (DMZ) -> API Gateway (Internal)
iptables -A FORWARD -p tcp -d 172.21.0.10 --dport 8000 -j ACCEPT

# 2. Allow Microcontrollers -> WeatherStation (UDP Ingress)
iptables -A FORWARD -p udp -d 172.21.0.14 --dport 9876 -j ACCEPT

# 3. Allow WeatherStation (Internal) -> Internet (UDP Egress to Clients)
# We allow traffic from WeatherStation to ANY destination on UDP
iptables -A FORWARD -p udp -s 172.21.0.14 -j ACCEPT

# 4. Allow Established/Related connections
iptables -A FORWARD -m state --state ESTABLISHED,RELATED -j ACCEPT

# ==========================================
# LOGGING
# ==========================================
iptables -A FORWARD -j LOG --log-prefix "FW-DROP: " --log-level 6

echo "Firewall rules applied."
