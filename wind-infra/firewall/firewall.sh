#!/bin/sh


# Flush existing rules
iptables -F
iptables -t nat -F
iptables -X


# Whitelist
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT


# Allow loopback (localhost) traffic
iptables -A INPUT -i lo -j ACCEPT

# Allow ICMP (Ping) for debugging
iptables -A INPUT -p icmp -j ACCEPT
iptables -A FORWARD -p icmp -j ACCEPT


# NAT RULES

# Masquerade weather station outbound traffic to Internet
iptables -t nat -A POSTROUTING -d 172.21.0.0/24 -j MASQUERADE

# HTTP: Nginx (DMZ) -> API Gateway (Internal)
# Traffic destined to Firewall IP (172.20.0.5) on port 8000
iptables -t nat -A PREROUTING -d 172.20.0.5 -p tcp --dport 8000 -j DNAT --to-destination 172.21.0.10:8000

# UDP: Microcontrollers (Internet) -> WeatherStation (Internal)
# Traffic destined to Firewall IP (172.20.0.5) on port 9876
iptables -t nat -A PREROUTING -d 172.20.0.5 -p udp --dport 9876 -j DNAT --to-destination 172.21.0.14:9876

# UDP: WeatherStation (Internal) -> EngClient (Internet)
# Traffic destined to EGRESS_HOST (172.21.0.5) on port 9877
iptables -t nat -A PREROUTING -d 172.21.0.5 -p udp --dport 9877 -j DNAT --to-destination 172.21.0.1:9877

# 3. Masquerade outbound traffic from Internal Net
iptables -t nat -A POSTROUTING -s 172.21.0.0/24 -j MASQUERADE


# FORWARDING RULES
# Here we prevent backdoors, as only specific traffics are allowed.

# Allow HTTP to API Gateway
iptables -A FORWARD -p tcp -d 172.21.0.10 --dport 8000 -j ACCEPT

# Allow UDP to WeatherStation
iptables -A FORWARD -p udp -d 172.21.0.14 --dport 9876 -j ACCEPT

# Allow WeatherStation to Internet
iptables -A FORWARD -p udp -s 172.21.0.14 -j ACCEPT

# Allow Established/Related connections
# Simplifies return traffic handling
iptables -A FORWARD -m state --state ESTABLISHED,RELATED -j ACCEPT


# LOGGING

# Log dropped INPUT packets
iptables -A FORWARD -j LOG --log-prefix "FW-DROP: " --log-level 6


echo "Firewall rules applied."
