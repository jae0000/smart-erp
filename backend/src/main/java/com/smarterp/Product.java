package com.smarterp;

public record Product(long productId, String productCode, String productName,
                      String price, long stockQty) {}
