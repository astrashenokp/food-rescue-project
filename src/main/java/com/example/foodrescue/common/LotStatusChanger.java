package com.example.foodrescue.common;

public interface LotStatusChanger {

    void transition(FoodLot lot, LotStatus next, String comment);
}
