///
/// Created by Kenny on 2025/4/15.
///

import 'package:equatable/equatable.dart';
import 'package:flutter/foundation.dart';

import 'uvccamera_rtmp_type.dart';

@immutable
class UvcCameraRtmpEvent extends Equatable {
  final UvcCameraRtmpType type;
  final dynamic? value;

  const UvcCameraRtmpEvent({required this.type, this.value});

  factory UvcCameraRtmpEvent.fromMap(Map<dynamic, dynamic> map) {
    return UvcCameraRtmpEvent(
      type: UvcCameraRtmpType.values.byName(map['type'] as String),
      value: map['value'],
    );
  }

  Map<String, dynamic> toMap() {
    return {'type': type.name};
  }

  @override
  List<Object?> get props => [type];
}
