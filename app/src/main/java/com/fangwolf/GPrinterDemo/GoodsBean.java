package com.fangwolf.GPrinterDemo;

/**
 * @Auther 獠牙血狼
 * @Date 2019/3/4
 * @Desc 模拟商品
 */
public class GoodsBean {
    String name;
    String amount;
    String price;

    public GoodsBean(String name, String amount, String price) {
        this.name = name;
        this.amount = amount;
        this.price = price;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }
}
