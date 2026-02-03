package edu.eci.arsw.primefinder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) {
        Control control = Control.newControl();
        
        control.start();
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        while(control.isAlive()){
            try {
                Thread.sleep(5000);
                
                control.pauseThreads();
                
                Thread.sleep(100);
                
                System.out.println("Numeros primos encontrados: " + control.getTotalPrimes());
                System.out.println("Presiona ENTER para continuar...");
                
                reader.readLine();
                
                control.resumeThreads();
                
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        control.waitForThreads();
        System.out.println("Total de primos encontrados: " + control.getTotalPrimes());

    }
	
}
