package com.netmoni.server.service;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.netmoni.server.dto.alert.AlertRequest;
import com.netmoni.server.dto.alert.AlertResponse;
import com.netmoni.server.entity.Alert;
import com.netmoni.server.entity.AlertStatus;
import com.netmoni.server.repository.AlertRepository;
import org.springframework.beans.factory.ObjectProvider;
import com.netmoni.server.service.AlertEmailNotifier;
@Service
public class AlertService {
	private final AlertRepository alerts;
	private final NetMoniEventPublisher events; private final ObjectProvider<AlertEmailNotifier> emailNotifier;
	public AlertService(AlertRepository alerts,NetMoniEventPublisher events,ObjectProvider<AlertEmailNotifier> emailNotifier){this.alerts=alerts;this.events=events;this.emailNotifier=emailNotifier;}
	@Transactional public AlertResponse create(AlertRequest request){
		Alert existing=alerts.findFirstByDeduplicationKeyAndStatusNot(request.deduplicationKey(),AlertStatus.RESOLVED).orElse(null);
		if(existing!=null)return response(existing);
		AlertResponse created=response(alerts.save(new Alert(request.deduplicationKey(),request.type(),request.severity(),request.message(),request.deviceId(),request.agentId())));
		events.publish("/topic/alerts","alert.created",created);
		if(created.severity()==com.netmoni.server.entity.AlertSeverity.CRITICAL) emailNotifier.ifAvailable(n->n.sendCritical(created));
		return created;
	}
	@Transactional(readOnly=true) public org.springframework.data.domain.Page<AlertResponse> find(AlertStatus status,com.netmoni.server.entity.AlertSeverity severity,int page,int size){
		if(page<0||size<1||size>100)throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 100");
		var request=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt"));
		var found=status!=null&&severity!=null?alerts.findBySeverityAndStatusOrderByCreatedAtDesc(severity,status,request):
			status!=null?alerts.findByStatusOrderByCreatedAtDesc(status,request):severity!=null?alerts.findBySeverityOrderByCreatedAtDesc(severity,request):alerts.findAllByOrderByCreatedAtDesc(request);
		return found.map(AlertService::response);
	}
	@Transactional public AlertResponse markRead(UUID id){Alert a=require(id);a.markRead();AlertResponse r=response(alerts.save(a));events.publish("/topic/alerts","alert.read",r);return r;}
	@Transactional public AlertResponse resolve(UUID id){Alert a=require(id);a.resolve();AlertResponse r=response(alerts.save(a));events.publish("/topic/alerts","alert.resolved",r);return r;}
	@Transactional public void resolveByKey(String key){alerts.findFirstByDeduplicationKeyAndStatusNot(key,AlertStatus.RESOLVED).ifPresent(a->{a.resolve();AlertResponse resolved=response(alerts.save(a));events.publish("/topic/alerts","alert.resolved",resolved);});}
	private Alert require(UUID id){return alerts.findById(id).orElseThrow(()->new AlertNotFoundException(id));}
	private static AlertResponse response(Alert a){return new AlertResponse(a.getId(),a.getType(),a.getSeverity(),a.getStatus(),a.getMessage(),a.getDeviceId(),a.getAgentId(),a.getCreatedAt(),a.getResolvedAt());}
}
