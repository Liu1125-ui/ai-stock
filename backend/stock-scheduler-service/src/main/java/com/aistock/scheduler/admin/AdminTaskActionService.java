package com.aistock.scheduler.admin;
import java.time.Instant;import java.util.UUID;
import org.springframework.stereotype.Service;
import static com.aistock.scheduler.admin.AdminTaskActionRecords.*;
@Service
public final class AdminTaskActionService {
 private final AdminTaskActionRepository repository;
 public AdminTaskActionService(AdminTaskActionRepository repository){this.repository=repository;}
 public Options options(UUID task,Instant now){return repository.options(task,now);}
 public CommandResult execute(UUID actor,UUID task,Action action,Command input,Instant now){return repository.execute(actor,task,action,input,now);}
 public Receipt receipt(UUID actor,UUID task,UUID operation){return repository.receipt(actor,task,operation);}
 public HistoryPage history(UUID task,HistoryQuery query,String cursor,Instant now){return repository.history(task,query,cursor,now);}
}
