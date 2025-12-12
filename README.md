<img width="2500" height="587" alt="WInD Banner" src="https://github.com/user-attachments/assets/e0ed35ae-818c-495e-b938-cfa4910e56b5" />

The **Weather Information Distributor** (WInD) is a distributed system made in Java to help handle weather data from various sources. This system was made as a assignment for the Distributed Programming course at UFERSA, Brazil. The project is designed to be modular and extensible, allowing for easy integration of new data sources and functionalities.


# Assignment Requirements
General requirements for the assignment include:
- **Java**: The system must be implemented in Java.
- **Distributed System**: The system must be distributed, allowing multiple components to run on different machines.

Functional requirements include:
- **Data Source**: The system must simulate microcontrollers that collect weather data from different regions. These data are generated randomly.
- **Data Processing**: A middleware component must process the collected data, parsing it to a common format.
- **Logging**: The system must implement logging for monitoring and debugging purposes.
- **Clients**: The system must provide two client applications: one for real time data visualization (engineer client) and another for historical data analysis (researcher client).
- **Dashboard**: A dashboard must be provided to visualize the data in real time.

Architectural requirements include:
- **UDP Protocol**: The system must use the UDP protocol for communication between components involved in the data collection and processing.
- **RabbitMQ**: The system must use RabbitMQ for communication between the middleware and the historical data consumer. (In this case, the application server that persists the data in a database).
- **Recovery**: The system must be able to recover from failures, ensuring data integrity and availability.
- **Microservices**: The system must be designed using a microservices architecture.

Technical requirements include:
- **Functional Interfaces**: The system's connections must use functional interfaces.
- **Java Streams**: The system must use Java Streams at some point.


# Architecture Overview
The architecture of WInD is designed to be modular and scalable. In the Image below, you can see the main components of the system and how they interact with each other.

![WInD - Whiteboard](https://github.com/user-attachments/assets/b053aba5-cf25-4a22-99e4-e993f323ddbd)

## Highlights
- **Docker**: The RabbitMQ server and other infrastructure components are containerized using Docker, allowing for easy deployment and management.
- **Load Balancing**: The system uses NGINX as a load balancer to distribute client requests.
- **RabbitMQ Consumer**: The RabbitMQ consumer is the Application Server. This allow for an easy database replication and a proxy cache implementation in the future, as all of them could also be a RabbitMQ consumer, granting data consistency between them.
- **Heartbeat Mechanism**: A service discovery component implements a heartbeat mechanism to monitor the health of the microservices.

## Security
The system implements a security layer to ensure data integrity, confidentiality, and availability.
- **Hybrid Cryptography**: Communications are secured using a hybrid approach combining RSA for key exchange, AES for data encryption, and HMAC for message integrity.
- **Authentication**: A custom Password Manager handles user authentication securely.
- **Reverse Proxy**: NGINX is used as a reverse proxy to manage client requests and hide the internal network topology.
- **Packet Filter**: `iptables` is configured to act as a firewall, filtering network traffic and protecting the system from unauthorized access.

## Technologies
- **Java**: The main programming language used for the system.
- **Maven**: The build tool used for managing dependencies and building the project.
- **RabbitMQ**: The message broker used for communication between the middleware and the historical data consumer.
- **Docker**: Used for containerizing the RabbitMQ server and other infrastructure components.
- **NGINX**: Used as a load balancer for client requests.
  

# Instructions
## Setup
To run the system, follow these steps:
1. **Clone the Repository**: Clone the repository to your local machine.
2. **Build the Project**: Use Maven to build the project. Run `mvn clean install` in the root directory of the project.

## Running the System
1. **Start the Docker Containers**: (A Linux environment is Recommended) Navigate to the `wind-infra` directory and run `docker-compose up --build` to start the required services.
2. **Start the Microcontrollers**: Navigate to the `microcontrollers` directory and run the microcontroller applications. Each microcontroller instance will start sending data via UDP.
3. **Start the Real Time Client**: At this point you can start the real time client application. Navigate to the `eng_client` directory and run the application. This will start receiving real time data via UDP.
4. **Start the Historical Client**: Finally, navigate to the `client` directory and run the Historical Client application. This will connect the client to the server and allow you to analyze historical data.
