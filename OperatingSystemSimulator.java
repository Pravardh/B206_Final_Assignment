import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;
import java.io.IOException;

/**
 * Represents a task the OS needs to run.
 */
class Job {
    int pid; // Unique ID
    int arrivalTimestamp; // When the job shows up
    int requiredCpuCycles; // How much CPU time it needs
    int memoryFootprint; // How much RAM it needs
    int remainingCpuCycles;
    int timeSpentWaiting;
    int totalTurnaroundTime;
    int finalCompletionTime;
    int assignedMemoryPartition = -1; // -1 means not in memory

    public Job(int pid, int arrival, int burst, int memory) {
        this.pid = pid;
        this.arrivalTimestamp = arrival;
        this.requiredCpuCycles = burst;
        this.remainingCpuCycles = burst;
        this.memoryFootprint = memory;
    }

    @Override
    public String toString() {
        return "Job " + pid + " (Mem: " + memoryFootprint + "MB)";
    }
}

/**
 * A single, fixed-size slot in RAM.
 */
class MemoryPartition {
    int partitionId;
    int capacity;
    boolean isAvailable;
    Job currentJob = null; // Track which job is in this partition

    public MemoryPartition(int id, int size) {
        this.partitionId = id;
        this.capacity = size;
        this.isAvailable = true;
    }
}

/**
 * Manages all memory, assigning jobs to partitions.
 */
class RAMController {
    private List<MemoryPartition> memoryPartitions;
    private int totalSystemMemory;

    public RAMController(List<Integer> partitionSizes) {
        this.memoryPartitions = new ArrayList<>();
        this.totalSystemMemory = 0;
        for (int i = 0; i < partitionSizes.size(); i++) {
            int size = partitionSizes.get(i);
            memoryPartitions.add(new MemoryPartition(i, size));
            this.totalSystemMemory += size;
        }
    }

    /**
     * Finds the first available partition that fits the job.
     */
    public int assignMemoryFirstFit(Job job) {
        for (int i = 0; i < memoryPartitions.size(); i++) {
            MemoryPartition partition = memoryPartitions.get(i);
            if (partition.isAvailable && partition.capacity >= job.memoryFootprint) {
                partition.isAvailable = false;
                partition.currentJob = job;
                return i;
            }
        }
        return -1; // No suitable partition found
    }

    /**
     * Finds the partition that fits the job most snugly, leaving the least wasted space.
     */
    public int assignMemoryBestFit(Job job) {
        int bestFitPartitionIndex = -1;
        int smallestWastedSpace = Integer.MAX_VALUE;

        for (int i = 0; i < memoryPartitions.size(); i++) {
            MemoryPartition partition = memoryPartitions.get(i);
            if (partition.isAvailable && partition.capacity >= job.memoryFootprint) {
                int wastedSpace = partition.capacity - job.memoryFootprint;
                if (wastedSpace < smallestWastedSpace) {
                    smallestWastedSpace = wastedSpace;
                    bestFitPartitionIndex = i;
                }
            }
        }

        if (bestFitPartitionIndex != -1) {
            MemoryPartition partition = memoryPartitions.get(bestFitPartitionIndex);
            partition.isAvailable = false;
            partition.currentJob = job;
        }
        return bestFitPartitionIndex;
    }

    /**
     * Frees up a memory partition after a job is done.
     */
    public void releaseMemory(int partitionIndex) {
        if (partitionIndex >= 0 && partitionIndex < memoryPartitions.size()) {
            MemoryPartition partition = memoryPartitions.get(partitionIndex);
            partition.isAvailable = true;
            partition.currentJob = null;
        }
    }
    
    /**
     * Calculates the current percentage of memory being used by jobs.
     */
    public double getCurrentMemoryUtilization() {
        if (totalSystemMemory == 0) return 0;
        
        double totalUsedMemory = 0;
        for (MemoryPartition partition : memoryPartitions) {
            if (!partition.isAvailable && partition.currentJob != null) {
                totalUsedMemory += partition.currentJob.memoryFootprint;
            }
        }
        return (totalUsedMemory / totalSystemMemory) * 100.0;
    }
}

/**
 * A blueprint for any CPU scheduling algorithm we want to implement.
 */
interface SchedulingAlgorithm {
    void run(List<Job> jobList, RAMController ram, String allocationStrategy);
}

/**
 * Processes jobs in the order they arrive (First-In, First-Out).
 */
class FirstComeFirstServe implements SchedulingAlgorithm {
    @Override
    public void run(List<Job> jobList, RAMController ram, String allocationStrategy) {
        System.out.println("\n>>> Running First-Come, First-Served Scheduler...");
        Collections.sort(jobList, Comparator.comparingInt(j -> j.arrivalTimestamp));
        int clock = 0;
        List<Job> finishedJobs = new ArrayList<>();
        double peakMemoryUtilization = 0.0;

        for (Job currentJob : jobList) {
            if (clock < currentJob.arrivalTimestamp) {
                clock = currentJob.arrivalTimestamp;
            }

            // Wait until a memory partition is available for the current job
            int partitionIndex = -1;
            while (partitionIndex == -1) {
                partitionIndex = "first".equalsIgnoreCase(allocationStrategy)
                               ? ram.assignMemoryFirstFit(currentJob)
                               : ram.assignMemoryBestFit(currentJob);
                
                peakMemoryUtilization = Math.max(peakMemoryUtilization, ram.getCurrentMemoryUtilization());

                if (partitionIndex == -1) {
                    System.out.println("Clock " + clock + ": " + currentJob + " waiting for memory.");
                    clock++; 
                }
            }
            currentJob.assignedMemoryPartition = partitionIndex;

            // Execute the job and calculate its metrics
            currentJob.timeSpentWaiting = clock - currentJob.arrivalTimestamp;
            System.out.println("Clock " + clock + ": Executing " + currentJob);
            clock += currentJob.requiredCpuCycles;
            currentJob.finalCompletionTime = clock;
            currentJob.totalTurnaroundTime = currentJob.finalCompletionTime - currentJob.arrivalTimestamp;
            
            finishedJobs.add(currentJob);
            ram.releaseMemory(currentJob.assignedMemoryPartition);
        }
        PerformanceTracker.displayReport(finishedJobs, peakMemoryUtilization);
    }
}

/**
 * Selects the waiting job with the smallest CPU burst time to run next.
 */
class ShortestJobFirst implements SchedulingAlgorithm {
    @Override
    public void run(List<Job> jobList, RAMController ram, String allocationStrategy) {
        System.out.println("\n>>> Running Shortest-Job-First Scheduler...");
        List<Job> readyJobs = new ArrayList<>();
        List<Job> finishedJobs = new ArrayList<>();
        int clock = 0;
        int jobsCompleted = 0;
        double peakMemoryUtilization = 0.0;

        while (jobsCompleted < jobList.size()) {
            // Add any newly arrived jobs to the ready queue
            for (Job job : jobList) {
                if (job.arrivalTimestamp <= clock && job.remainingCpuCycles > 0 && !readyJobs.contains(job)) {
                    readyJobs.add(job);
                }
            }

            if (readyJobs.isEmpty()) {
                clock++;
                continue;
            }
            
            // Sort ready jobs by burst time (shortest first) to find the next one to run
            readyJobs.sort(Comparator.comparingInt((Job j) -> j.requiredCpuCycles)
                                     .thenComparingInt(j -> j.arrivalTimestamp));

            Job currentJob = readyJobs.get(0);
            
            int partitionIndex = "first".equalsIgnoreCase(allocationStrategy)
                               ? ram.assignMemoryFirstFit(currentJob)
                               : ram.assignMemoryBestFit(currentJob);
            
            peakMemoryUtilization = Math.max(peakMemoryUtilization, ram.getCurrentMemoryUtilization());

            // If no memory, wait and try again in the next cycle
            if (partitionIndex == -1) {
                System.out.println("Clock " + clock + ": " + currentJob + " is shortest but must wait for memory.");
                clock++;
                continue;
            }
            currentJob.assignedMemoryPartition = partitionIndex;
            
            // Execute the job
            System.out.println("Clock " + clock + ": Executing " + currentJob);
            currentJob.timeSpentWaiting = clock - currentJob.arrivalTimestamp;
            clock += currentJob.requiredCpuCycles;
            currentJob.finalCompletionTime = clock;
            currentJob.totalTurnaroundTime = currentJob.finalCompletionTime - currentJob.arrivalTimestamp;
            currentJob.remainingCpuCycles = 0;

            finishedJobs.add(currentJob);
            readyJobs.remove(currentJob);
            ram.releaseMemory(currentJob.assignedMemoryPartition);
            jobsCompleted++;
        }
        PerformanceTracker.displayReport(finishedJobs, peakMemoryUtilization);
    }
}

/**
 * Gives each job a small turn (time slice) on the CPU in a circular fashion.
 */
class RoundRobin implements SchedulingAlgorithm {

    @Override
    public void run(List<Job> jobList, RAMController ram, String allocationStrategy) {
        System.out.println("\n>>> Running Round Robin Scheduler...");
        Scanner inputReader = new Scanner(System.in);
        System.out.print("Enter Time Slice (Quantum): ");
        int timeSlice = inputReader.nextInt();

        Queue<Job> executionQueue = new LinkedList<>();
        List<Job> finishedJobs = new ArrayList<>();
        int clock = 0;
        int jobIndex = 0; // To track jobs from the main list
        double peakMemoryUtilization = 0.0;

        Collections.sort(jobList, Comparator.comparingInt(j -> j.arrivalTimestamp));

        while (finishedJobs.size() < jobList.size()) {
            // Add newly arrived jobs to the end of the queue
            while (jobIndex < jobList.size() && jobList.get(jobIndex).arrivalTimestamp <= clock) {
                executionQueue.add(jobList.get(jobIndex));
                jobIndex++;
            }

            if (executionQueue.isEmpty()) {
                clock++;
                continue;
            }

            Job currentJob = executionQueue.poll();
            
            // If job doesn't have memory, try to assign it
            if (currentJob.assignedMemoryPartition == -1) {
                 int partitionIndex = "first".equalsIgnoreCase(allocationStrategy) 
                                    ? ram.assignMemoryFirstFit(currentJob) 
                                    : ram.assignMemoryBestFit(currentJob);
                 
                 peakMemoryUtilization = Math.max(peakMemoryUtilization, ram.getCurrentMemoryUtilization());

                 // If it fails, put it back in the queue and try again later
                 if (partitionIndex == -1) {
                     System.out.println("Clock " + clock + ": " + currentJob + " can't get memory, will retry.");
                     executionQueue.add(currentJob); 
                     clock++;
                     continue;
                 }
                 currentJob.assignedMemoryPartition = partitionIndex;
            }

            System.out.println("Clock " + clock + ": Executing " + currentJob);
            int runTime = Math.min(timeSlice, currentJob.remainingCpuCycles);
            currentJob.remainingCpuCycles -= runTime;
            clock += runTime;

            // Check for new arrivals during the last time slice
            while (jobIndex < jobList.size() && jobList.get(jobIndex).arrivalTimestamp <= clock) {
                executionQueue.add(jobList.get(jobIndex));
                jobIndex++;
            }

            // If the job is finished, record stats and release memory. Otherwise, add it back to the queue.
            if (currentJob.remainingCpuCycles == 0) {
                currentJob.finalCompletionTime = clock;
                currentJob.totalTurnaroundTime = currentJob.finalCompletionTime - currentJob.arrivalTimestamp;
                currentJob.timeSpentWaiting = currentJob.totalTurnaroundTime - currentJob.requiredCpuCycles;
                finishedJobs.add(currentJob);
                ram.releaseMemory(currentJob.assignedMemoryPartition);
            } else {
                executionQueue.add(currentJob);
            }
        }
        PerformanceTracker.displayReport(finishedJobs, peakMemoryUtilization);
    }
}

/**
 * Calculates and prints a final report on system performance.
 */
class PerformanceTracker {
    public static void displayReport(List<Job> finishedJobs, double peakMemoryUtilization) {
        if (finishedJobs.isEmpty()) {
            System.out.println("No jobs were processed.");
            return;
        }

        double totalWait = 0;
        double totalTurnaround = 0;

        System.out.println("\n--- System Performance Report ---");
        System.out.println("=================================================");
        System.out.println("Job ID | Waiting Time | Turnaround Time");
        System.out.println("-------------------------------------------------");
        for (Job job : finishedJobs) {
            System.out.printf("%-6d | %-12d | %-15d\n", job.pid, job.timeSpentWaiting, job.totalTurnaroundTime);
            totalWait += job.timeSpentWaiting;
            totalTurnaround += job.totalTurnaroundTime;
        }
        System.out.println("=================================================");

        double avgWait = totalWait / finishedJobs.size();
        double avgTurnaround = totalTurnaround / finishedJobs.size();
        
        System.out.printf("Average Wait Time: %.2f\n", avgWait);
        System.out.printf("Average Turnaround Time: %.2f\n", avgTurnaround);
        System.out.printf("Peak Memory Utilization: %.2f%%\n", peakMemoryUtilization);
        System.out.println("-------------------------------------------------");
    }
}

/**
 * The main entry point for the simulator. It gets user input and runs the simulation.
 */
public class OperatingSystemSimulator {

    public static void main(String[] args) {
    
        Scanner userInput = new Scanner(System.in);
        List<Job> jobQueue = new ArrayList<>();

        // --- Get user input for memory and job configurations ---
        System.out.println("--- OS Kernel Simulator Setup ---");

        System.out.print("Enter number of memory partitions: ");
        int partitionCount = userInput.nextInt();
        List<Integer> partitionSizes = new ArrayList<>();
        System.out.println("Enter sizes for each memory partition:");
        for (int i = 0; i < partitionCount; i++) {
            System.out.print("Partition #" + (i + 1) + " size (MB): ");
            partitionSizes.add(userInput.nextInt());
        }

        RAMController ram = new RAMController(partitionSizes);

        System.out.print("Enter number of jobs to simulate: ");
        int jobCount = userInput.nextInt();

        for (int i = 0; i < jobCount; i++) {
            System.out.println("\n--- Configuring Job #" + (i + 1) + " ---");
            System.out.print("Arrival Time: ");
            int arrival = userInput.nextInt();
            
            int burst = -1;
            while (burst <= 0) {
                System.out.print("CPU Burst Time (must be > 0): ");
                burst = userInput.nextInt();
                if (burst <= 0) System.out.println("Invalid input.");
            }

            int mem = -1;
            while (mem <= 0) {
                System.out.print("Memory Footprint (MB, must be > 0): ");
                mem = userInput.nextInt();
                if (mem <= 0) System.out.println("Invalid input.");
            }
            jobQueue.add(new Job(i + 1, arrival, burst, mem));
        }

        // --- Let the user choose the algorithms ---
        System.out.println("\n--- Select CPU Scheduling Algorithm ---");
        System.out.println("1. First-Come, First-Served");
        System.out.println("2. Shortest-Job-First (Non-Preemptive)");
        System.out.println("3. Round Robin");
        System.out.print("Enter choice [1-3]: ");
        int schedulerChoice = userInput.nextInt();

        System.out.println("\n--- Select Memory Allocation Strategy ---");
        System.out.println("1. First-Fit");
        System.out.println("2. Best-Fit");
        System.out.print("Enter choice [1-2]: ");
        int memoryChoice = userInput.nextInt();
        String memoryStrategy = (memoryChoice == 1) ? "first" : "best";

        System.out.println("Clearing screen...");
        clearConsole();

        // --- Run the simulation with the selected algorithms ---
        SchedulingAlgorithm scheduler;
        switch (schedulerChoice) {
            case 1:
                scheduler = new FirstComeFirstServe();
                break;
            case 2:
                scheduler = new ShortestJobFirst();
                break;
            case 3:
                scheduler = new RoundRobin();
                break;
            default:
                System.out.println("Invalid scheduler choice. Terminating.");
                userInput.close();
                return;
        }

        scheduler.run(new ArrayList<>(jobQueue), ram, memoryStrategy);
        userInput.close();
    }

    /**
     * A helper method to clear the console screen (works on Windows and Linux/Mac).
     */
    public static void clearConsole() {
        try {
            final String os = System.getProperty("os.name");
            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // Silently fail if clearing the console doesn't work.
        }
    }
}