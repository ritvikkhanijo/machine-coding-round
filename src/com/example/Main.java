package com.example;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {

    static class Edge {
        String airline;
        String src;
        String dst;
        int price;
        Set<String> properties;

        // need to add arrival / departure times for bonus

        Edge(String airline, String src, String dst, int price, Set<String> properties) {
            this.airline = airline;
            this.src = src;
            this.dst = dst;
            this.price = price;
            this.properties = properties;
        }

        boolean hasProperty(String prop) {
            return properties != null && properties.contains(prop);
        }
    }

    static class Path {
        List<Edge> legs = new ArrayList<>();
        int totalCost = 0;

        void add(Edge e) {
            legs.add(e);
            totalCost += e.price;
        }

        int hops() {
            return legs.size();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (Edge e : legs) {
                sb.append(e.src).append(" to ").append(e.dst)
                        .append(" via ").append(e.airline)
                        .append(" for ").append(e.price).append("\n");
            }
            sb.append("\nTotal Flights = ").append(hops()).append("\n");
            sb.append("Total Cost = ").append(totalCost).append("\n");
            return sb.toString();
        }
    }

    static class Graph {
        Map<String, List<Edge>> adj = new HashMap<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        void addEdge(Edge e) {
            lock.writeLock().lock();
            try {
                adj.computeIfAbsent(e.src, k -> new ArrayList<>()).add(e);
            } finally {
                lock.writeLock().unlock();
            }
        }

        List<Edge> neighbors(String city) {
            lock.readLock().lock();
            try {
                return adj.getOrDefault(city, Collections.emptyList());
            } finally {
                lock.readLock().unlock();
            }
        }
    }

    static class FlightFilter {
        boolean mealsRequired;

        FlightFilter(boolean mealsRequired) {
            this.mealsRequired = mealsRequired;
        }

        boolean allow(Edge e) {
            if (mealsRequired) {
                return e.hasProperty("meals");
            }
            return true;
        }
    }

    static class FlightSystem {
        Graph g = new Graph();

        void registerFlight(String airline, String src, String dst, int price, boolean meals) {
            Set<String> props = new HashSet<>();
            if (meals || airline.equalsIgnoreCase("IndiGo")) {
                props.add("meals");
            }
            Edge e = new Edge(airline, src, dst, price, props);
            g.addEdge(e);
            System.out.println(airline + " " + src + " -> " + dst + " registered");
        }

        void searchFlights(String src, String dst, boolean mealsRequired) {
            try {
                if (src.equals(dst)) {
                    throw new IllegalArgumentException("Source and destination cannot be the same.");
                }

                FlightFilter filter = new FlightFilter(mealsRequired);

                Path minHopsPath = findMinHopsPath(src, dst, filter);
                Path cheapestPath = findCheapestPath(src, dst, filter);

                System.out.println("* Route with Minimum Hops:");
                if (minHopsPath == null) {
                    System.out.println("No route found\n");
                } else {
                    System.out.println(minHopsPath);
                }

                System.out.println("* Cheapest Route:");
                if (cheapestPath == null) {
                    System.out.println("No route found\n");
                } else {
                    System.out.println(cheapestPath);
                }

            } catch (IllegalArgumentException e) {
                System.out.println("Error: " + e.getMessage() + "\n");
            }
        }

        Path findMinHopsPath(String src, String dst, FlightFilter filter) {
            Queue<Path> queue = new LinkedList<>();
            Path start = new Path();
            queue.add(start);

            Set<String> visited = new HashSet<>();
            visited.add(src);

            Path best = null;
            int minHops = Integer.MAX_VALUE;

            while (!queue.isEmpty()) {
                Path curr = queue.poll();
                String currentCity = curr.legs.isEmpty() ? src : curr.legs.get(curr.legs.size() - 1).dst;

                if (currentCity.equals(dst)) {
                    // add conditionn for best cost in case of tie
                    if (curr.hops() < minHops || (curr.hops() == minHops && (best == null || curr.totalCost < best.totalCost))) {
                        best = curr;
                        minHops = curr.hops();
                    }
                    continue;
                }

                for (Edge e : g.neighbors(currentCity)) {
                    if (!filter.allow(e)) continue;
                    if (curr.hops() + 1 > minHops) continue;

                    Path newPath = clonePath(curr);
                    newPath.add(e);
                    queue.add(newPath);
                }
            }
            return best;
        }

        Path findCheapestPath(String src, String dst, FlightFilter filter) {
            PriorityQueue<Path> pq = new PriorityQueue<>(Comparator.comparingInt(p -> p.totalCost));
            Path start = new Path();
            pq.add(start);

            Map<String, Integer> bestCost = new HashMap<>();
            bestCost.put(src, 0);

            while (!pq.isEmpty()) {
                Path curr = pq.poll();
                String currentCity = curr.legs.isEmpty() ? src : curr.legs.get(curr.legs.size() - 1).dst;

                if (currentCity.equals(dst)) {
                    return curr;
                }

                for (Edge e : g.neighbors(currentCity)) {
                    if (!filter.allow(e)) continue;

                    Path newPath = clonePath(curr);
                    newPath.add(e);

                    int prevCost = bestCost.getOrDefault(e.dst, Integer.MAX_VALUE);
                    if (newPath.totalCost < prevCost) {
                        bestCost.put(e.dst, newPath.totalCost);
                        pq.add(newPath);
                    }
                }
            }
            return null;
        }

        Path clonePath(Path p) {
            Path np = new Path();
            np.legs.addAll(p.legs);
            np.totalCost = p.totalCost;
            return np;
        }
    }

    public static void main(String[] args) {
        FlightSystem flights = new FlightSystem();

        flights.registerFlight("JetAir", "DEL", "BLR", 500, false);
        flights.registerFlight("JetAir", "BLR", "LON", 1000, false);
        flights.registerFlight("Delta", "DEL", "LON", 2000, false);
        flights.registerFlight("Delta", "LON", "NYC", 2000, false);
        flights.registerFlight("IndiGo", "LON", "NYC", 2500, true);
        flights.registerFlight("IndiGo", "DEL", "BLR", 600, true);
        flights.registerFlight("IndiGo", "BLR", "PAR", 800, false);
        flights.registerFlight("IndiGo", "PAR", "LON", 300, true);

        System.out.println("\nSearching flights DEL -> NYC:");
        flights.searchFlights("DEL", "NYC", false);

        System.out.println("\nSearching flights DEL -> NYC with Meals:");
        flights.searchFlights("DEL", "NYC", true);
    }
}
