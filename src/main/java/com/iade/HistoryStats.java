package com.iade;

/** Iade gecmisi tablosundan hesaplanan toplamlar (istatistik ekrani icin). */
public final class HistoryStats {

    public final int approved;
    public final int claimed;
    public final int deleted;
    /** Ban tespitinden onaya kadar gecen ortalama sure (ms); veri yoksa -1. */
    public final long averageApprovalMillis;

    public HistoryStats(int approved, int claimed, int deleted, long averageApprovalMillis) {
        this.approved = approved;
        this.claimed = claimed;
        this.deleted = deleted;
        this.averageApprovalMillis = averageApprovalMillis;
    }

    static HistoryStats empty() {
        return new HistoryStats(0, 0, 0, -1L);
    }
}
