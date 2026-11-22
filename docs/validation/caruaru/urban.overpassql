[out:json][timeout:25];
(
 node["highway"="speed_camera"](-8.34,-36.06,-8.2,-35.9);
 relation["type"="enforcement"]["enforcement"~"(^|;)(maxspeed|average_speed|traffic_signals)(;|$)"](-8.34,-36.06,-8.2,-35.9);
);
out body;
>;
out skel qt;
