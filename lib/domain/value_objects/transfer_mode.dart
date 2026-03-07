/// 传分模式：仅 DivingFish / 仅 LXNS / 双平台。
enum TransferMode {
  divingFishOnly,
  lxnsOnly,
  both;

  bool get needsDivingFish => this == divingFishOnly || this == both;
  bool get needsLxns => this == lxnsOnly || this == both;
}
