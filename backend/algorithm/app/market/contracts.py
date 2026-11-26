"""HTTP request contract for a bounded single stock collection."""
from pydantic import BaseModel, Field
class CollectRequest(BaseModel):
    operationId:str=Field(min_length=1,max_length=128)
    provider:str='baostock'
    instrumentId:str
    start:str
    end:str
