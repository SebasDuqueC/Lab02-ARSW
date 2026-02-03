/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.eci.arsw.primefinder;

/**
 *
 */
public class Control extends Thread {
    
    private final static int NTHREADS = 3;
    private final static int MAXVALUE = 30000000;
    private final static int TMILISECONDS = 5000;

    private final int NDATA = MAXVALUE / NTHREADS;

    private PrimeFinderThread pft[];
    private Object lock;
    
    private Control() {
        super();
        this.lock = new Object();
        this.pft = new  PrimeFinderThread[NTHREADS];

        int i;
        for(i = 0;i < NTHREADS - 1; i++) {
            PrimeFinderThread elem = new PrimeFinderThread(i*NDATA, (i+1)*NDATA, lock);
            pft[i] = elem;
        }
        pft[i] = new PrimeFinderThread(i*NDATA, MAXVALUE + 1, lock);
    }
    
    public static Control newControl() {
        return new Control();
    }

    @Override
    public void run() {
        for(int i = 0;i < NTHREADS;i++ ) {
            pft[i].start();
        }
    }
    
    public void pauseThreads(){
        for(int i = 0; i < NTHREADS; i++){
            pft[i].pauseThread();
        }
    }
    
    public void resumeThreads(){
        synchronized(lock){
            for(int i = 0; i < NTHREADS; i++){
                pft[i].resumeThread();
            }
            lock.notifyAll();
        }
    }
    
    public int getTotalPrimes(){
        int total = 0;
        for(int i = 0; i < NTHREADS; i++){
            total += pft[i].getPrimes().size();
        }
        return total;
    }
    
    public void waitForThreads(){
        for(int i = 0;i < NTHREADS;i++ ) {
            try {
                pft[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    
}
