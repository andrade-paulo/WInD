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
- **MQTT Protocol**: The system must use the MQTT protocol for communication between components involved in the data collection and processing.
- **MQTT Brokers**: Two brokers must be provided: one for the communication between microcontrollers and the middleware, and another for the communication between the middleware and the real time clients.
- **RabbitMQ**: The system must use RabbitMQ for communication between the middleware and the historical data consumer. (In this case, the application server that persists the data in a database).
- **Recovery**: The system must be able to recover from failures, ensuring data integrity and availability.

Technical requirements include:
- **Functional Interfaces**: The system's connections must use functional interfaces.
- **Java Streams**: The system must use Java Streams at some point.
- **Sockets**: The system must implement a socket level communication.


# Architecture Overview
The architecture of WInD is designed to be modular and scalable. In the Image below, you can see the main components of the system and how they interact with each other.

<img width="6935" height="4100" alt="WInD - Whiteboard" src="https://github.com/user-attachments/assets/cec9f839-c1a1-4849-920e-449f4dc14313" />

## Highlights
- **Docker**: The brokers and the RabbitMQ server are containerized using Docker, allowing for easy deployment and management.
- **Proxy Balancing**: The system uses the LocationServer to balance the proxy connections.
- **RabbitMQ Consumer**: The RabbitMQ consumer is the Application Server. This allow for an easy database replication and a proxy cache implementation in the future, as all of them could also be a RabbitMQ consumer, granting data consistency between them.
  

# Instructions
## Setup
To run the system, follow these steps:
1. **Clone the Repository**: Clone the repository to your local machine.
2. **Build the Project**: Use Maven to build the project. Run `mvn clean install` in the root directory of the project.

## Running the System
1. **Start the Docker Containers**: (A Linux environment is Recommended) Navigate to the `wind-infra` directory and run `docker-compose up -d` to start the required services.
2. **Start the Microcontrollers**: Navigate to the `microcontrollers` directory and run the microcontroller applications. Each microcontroller instance will start sending data to the MQTT broker.
3. **Start the Weather Station (Middleware)**: Navigate to the `weather_station` directory and run the Weather Station application. This will start processing the data from the microcontrollers.
4. **Start the Real Time Client**: At this point you can start the real time client application. Navigate to the `eng_client` directory and run the application. This will connect to the MQTT broker and start receiving real time data.'
5. **Start the Application Server**: Navigate to the `application_server` directory and run the Application Server. This will start consuming data from RabbitMQ and persisting it in the database.
6. **Start the Location Server**: Navigate to the `location_server` directory and run the Location Server. This will start balancing the proxy connections.
7. **Start the Proxy**: Navigate to the `proxy_server` directory and run the Proxy application. This will start the proxy server, allowing clients to connect to the system.
8. **Start the Historical Client**: Finally, navigate to the `client` directory and run the Historical Client application. This will connect the client to a proxy and allow you to analyze historical data.
