package com.gikk.scheduler;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
/** 
 * This class handles scheduling of tasks that should be executed one or more times sometime in the future.<br><br>
 * 
 * This class uses a ScheduledThreadPoolExecutor to execute the different tasks. That has the consequence that a thread
 * might be running for a while after the program tells the Scheduler to terminate. <b>This is normal and may take up to 60 seconds.</b>
 * After 60 seconds the Scheduler force-terminates all remaining tasks.
 * 
 * @author Gikkman
 *
 */
public class Scheduler {
	//***********************************************************************************************
	//											VARIABLES
	//***********************************************************************************************
	private final ScheduledThreadPoolExecutor executor;
	private boolean disposed = false;

	//***********************************************************************************************
	//											CONSTRUCTOR
	//***********************************************************************************************
	Scheduler (SchedulerBuilder builder) { 	
		ThreadFactory threadFactory = new GikkThreadFactory.Builder()
													 	   .setThreadsPrefix( builder.threadsPrefix )
													 	   .setThreadsDaemon( builder.daemonThreads )
													 	   .build();
		this.executor = new ScheduledThreadPoolExecutor( builder.capacity , threadFactory);
	}	
	//***********************************************************************************************
	//											PUBLIC
	//***********************************************************************************************
	/**Schedules a Task to be executed once every {@code periodMillis}. The Task's {@code onUpdate()} will be called
	 * the first time after initDelayMillis.
	 * 
	 * @param initDelayMillis How many milliseconds we wait until we start trying to execute this task
	 * @param periodMillis How many milliseconds we wait until we start trying to execute this task from the previous time it executed
	 * @param task The task to the executed repeatedly
	 * @return A {@code ScheduledFuture}, which may be used to interact with the scheduled task (say for canceling or interruption)
	 */
	ScheduledFuture<?> scheduleRepeatedTask(int initDelayMillis, int periodMillis, final GikkTask task) {	
		Runnable runnable = wrapRunnable(task);
		return executor.scheduleAtFixedRate( runnable , initDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
	}

	/**Postpones a OneTimeTask for delayMillis. After the assigned delay, the task will be performed as soon as possible. 
	 * The task might have to wait for longer, if no threads are availible after the stated delay.<br>
	 * The task will be executed only once, and then removed from the scheduler. Results from the task may be recovered from
	 * the {@code ScheduledFuture} object after completion.
	 * 
	 * @param delayMillis How many milliseconds we wait until we start trying to execute this task
	 * @param task The task to be executed
	 * @return A {@code ScheduledFuture}, which may be used to interact with the scheduled task (say for canceling or interruption)
	 */
	ScheduledFuture<?> scheduleDelayedTask(int delayMillis, GikkTask task) {
		Runnable runnable = wrapRunnable(task);
		return executor.schedule( runnable , delayMillis, TimeUnit.MILLISECONDS);
	}
	
	/**Tells the scheduler to perform a  certain task as soon as possible. This might be immediately (if there are threads
	 * availible) or sometime in the future. There are no guarantees for when the task will be performed, just as soon as possible.
	 * 
	 * @param task The task that should be performed
	 * @return A {@code ScheduledFuture}, which may be used to interact with the scheduled task (say for canceling or interruption)
	 */
	ScheduledFuture<?> executeTask(GikkTask task){
		Runnable runnable = wrapRunnable(task);
		return executor.schedule( runnable , 0, TimeUnit.MILLISECONDS);
	}
	//***********************************************************************************************
	//											TERMINATION
	//***********************************************************************************************
	/**This method will create a new thread, that will try to shut down execution of all tasks.<br><br>
	 * 
	 * First, the Thread will initiates an orderly shutdown in which previously submitted tasks are executed, 
	 * but no new tasks will be accepted. Submitted in this case means that the task has begun execution. The thread
	 * will then allow up to 60 seconds for Tasks to complete.<br>
	 * After waiting for 60 seconds, the Thread will attempt to stop all actively executing tasks and halt the process 
	 * of waiting tasks.<br><br>
	 * 
	 * The created thread is not a daemon thread, so if it is not possible to halt execution of all tasks, the new thread
	 * might never finish. Thus, it is important that created tasks can be halted.
	 * 
	 */
	public void onProgramExit() {	
		synchronized(this){
			if( disposed == true )
				return;	
			disposed = true;
		}
		
		Thread thread = new Thread( new Runnable() {
			@Override
			public void run(){
				try {
					System.out.println();
					System.out.println("\tThere are currently " + executor.getQueue().size()+ " task scheduled.\n"
									 + "\tThere are currently " + executor.getActiveCount() + " tasks executing.\n"
							 		 + "\tAttempting shutdown. Please allow up to a minute...");
					
					executor.shutdown(); 
					if( executor.awaitTermination(60, TimeUnit.SECONDS) ) {
						return;
					}
				
					System.out.println();
					System.out.println("\tThere are still " + executor.getActiveCount() + " tasks executing.\n"
									 + "\tForcing shutdown...");
					executor.shutdownNow();
				} catch (Exception e) {
					e.printStackTrace();
				}
		} } );	
		
		thread.setDaemon( false );
		thread.start();
	}
	
	//***********************************************************************************************
	//											PRIVATE
	//***********************************************************************************************
	private Runnable wrapRunnable(final GikkTask task) {
		return new Runnable() {		
			@Override
			public void run() {
				task.onExecute();
			}
		};
	}
}
