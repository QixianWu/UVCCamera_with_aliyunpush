import 'package:equatable/equatable.dart';
import 'package:flutter/cupertino.dart';

import 'uvccamera_frame_format.dart';

@immutable
class UvcCameraMode extends Equatable {
  final int frameWidth;
  final int frameHeight;
  final UvcCameraFrameFormat frameFormat;

  final int maxFps;

  const UvcCameraMode(
      {required this.frameWidth, required this.frameHeight, required this.frameFormat, this.maxFps = 60});

  /// The aspect ratio of the camera mode.
  double get aspectRatio => frameWidth / frameHeight;

  factory UvcCameraMode.fromMap(Map<dynamic, dynamic> map) {
    return UvcCameraMode(
      frameWidth: map['frameWidth'] as int,
      frameHeight: map['frameHeight'] as int,
      frameFormat: UvcCameraFrameFormat.values.byName(map['frameFormat'] as String),
    );
  }

  Map<String, dynamic> toMap() {
    return {'frameWidth': frameWidth, 'frameHeight': frameHeight, 'frameFormat': frameFormat.name, 'maxFps': maxFps};
  }

  UvcCameraMode copyWith({
    int? frameWidth,
    int? frameHeight,
    UvcCameraFrameFormat? frameFormat,
    int? maxFps,
  }) {
    return UvcCameraMode(
      frameWidth: frameWidth ?? this.frameWidth,
      frameHeight: frameHeight ?? this.frameHeight,
      frameFormat: frameFormat ?? this.frameFormat,
      maxFps: maxFps ?? this.maxFps,
    );
  }

  @override
  List<Object?> get props => [frameWidth, frameHeight, frameFormat, maxFps];
}
