package com.mrcrayfish.vehicle.paper.runtime;

/** Ciclul AB — cronometrul tick-ului de vehicule (comanda /vehicle perf).
 *  Cost propriu: ~50ns/tick (2x System.nanoTime + o medie exponentiala) —
 *  adica nimic. Fereastra de 60s e un inel mic fixat static; nu se aloca
 *  nimic la runtime. Comanda doar CITESTE contorii acestia, nu porneste
 *  nicio scanare de fundal pe server. */
public final class PerfTracker {

    private static final int WINDOW_SECONDS = 60;
    /** suma nanos per secunda (inel de 60 de secunde). */
    private static final double[] SECOND_SUM = new double[WINDOW_SECONDS];
    /** varful de tick per secunda. */
    private static final double[] SECOND_MAX = new double[WINDOW_SECONDS];
    private static int windowIndex = 0;
    private static long currentSecond = -1L;
    private static double secondSumNanos = 0.0D;
    private static double secondMaxNanos = 0.0D;
    /** media exponentiala (instant, reactioneaza lin) a duratei unui tick. */
    private static double emaNanos = 0.0D;

    private PerfTracker() {
    }

    /** Cheamat la INCEPUTUL tickului VehicleManager. */
    public static long begin() {
        return System.nanoTime();
    }

    /** Cheamat la FINALUL tickului VehicleManager (in finally — si pe exceptie). */
    public static void end(long startedNanos) {
        double elapsed = (double) (System.nanoTime() - startedNanos);
        emaNanos = emaNanos == 0.0D ? elapsed : emaNanos * 0.98D + elapsed * 0.02D;
        long nowSecond = System.currentTimeMillis() / 1000L;
        if (nowSecond != currentSecond) {
            if (currentSecond >= 0L) {
                SECOND_SUM[windowIndex] = secondSumNanos;
                SECOND_MAX[windowIndex] = secondMaxNanos;
                windowIndex = (windowIndex + 1) % WINDOW_SECONDS;
            }
            // secundele fara tick (server hiberneaza/lageste) raman pe valori vechi
            // pana le acopera rotatia - acceptabil pentru un indicator live
            secondSumNanos = 0.0D;
            secondMaxNanos = 0.0D;
            currentSecond = nowSecond;
        }
        secondSumNanos += elapsed;
        if (elapsed > secondMaxNanos) {
            secondMaxNanos = elapsed;
        }
    }

    /** Medie exponentiala, in milisecunde. */
    public static double emaMillis() {
        return emaNanos / 1.0e6D;
    }

    /** Media per tick pe ultimul minut (suma/secunda impartita la 20 de tickuri), ms. */
    public static double avgPerTickLastMinuteMillis() {
        double total = 0.0D;
        for (double sum : SECOND_SUM) {
            total += sum;
        }
        return total / (WINDOW_SECONDS * 20.0D) / 1.0e6D;
    }

    /** Cel mai scump tick din ultimul minut, ms. */
    public static double maxTickLastMinuteMillis() {
        double max = 0.0D;
        for (double m : SECOND_MAX) {
            if (m > max) {
                max = m;
            }
        }
        return max / 1.0e6D;
    }
}
